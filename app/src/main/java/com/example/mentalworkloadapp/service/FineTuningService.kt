package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.mentalworkloadapp.data.local.db.AppDatabase
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import com.example.mentalworkloadapp.data.repository.EegRepository
import com.example.mentalworkloadapp.util.EegFeatureExtractor
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.widget.Toast
import com.example.mentalworkloadapp.notification.FineTuningNotification
import android.app.NotificationManager


class FineTuningService : Service() {
    companion object {
        var isRunning = false
        private const val TAG = "FineTuningService"
    }

    private lateinit var notificationHelper: FineTuningNotification
    private lateinit var notificationManager: NotificationManager
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        notificationHelper = FineTuningNotification(this@FineTuningService)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationHelper.createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val notification = notificationHelper.createFineTuningStartedNotification()
        startForeground(FineTuningNotification.NOTIFICATION_ID, notification)

        //startForeground(FineTuningNotification.NOTIFICATION_ID, notificationHelper.createFineTuningStartedNotification())
        //when service is started
        serviceScope.launch {
            try {
                //start fine tuning
                fineTuning()
            } catch (e: Exception) {
                //in case of exceptions
                withContext(Dispatchers.Main) {
                    notificationHelper.showNotification(notificationHelper.createGenericErrorNotification(), FineTuningNotification.NOTIFICATION_ID+2)
                }
                stopSelf()
            } finally {
                //stop the service
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceJob.cancel() // cancel all coroutines when service is destroyed
        try {
            stopForeground(STOP_FOREGROUND_DETACH)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }

        super.onDestroy()
    }

    private suspend fun fineTuning() {
        val sampleEegDao = DatabaseProvider.getDatabase(this).sampleEegDao()
        val repository = EegRepository(sampleEegDao)

        try {
            val modelFile = loadModelFromFile("trainable_model.tflite")
            val interpreter = Interpreter(modelFile)

            //getting the number of session available
            val SAMPLES_PER_SESSION = 18000 // 3 minutes of data (3 * 60 * 100)
            val SAMPLES_PER_MINI_SESSION = 180  // 1.8 seconds of data (1.8 * 100)


            val samplesAvailable=sampleEegDao.countSamples()
            val sessionsAvailable:Int= (samplesAvailable/SAMPLES_PER_SESSION).toInt()
            // Checking if there is enough data
            val MIN_SESSIONS_REQUIRED = 4
            if (sessionsAvailable < MIN_SESSIONS_REQUIRED) {
                withContext(Dispatchers.Main) {
                    notificationHelper.showNotification(notificationHelper.createNotEnoughDataErrorNotification(MIN_SESSIONS_REQUIRED-sessionsAvailable), FineTuningNotification.NOTIFICATION_ID + 3)
                }
                stopSelf() // Stop the service if not enough data
                return
            }

            //for each 3-min session
            for (i in 0 until sessionsAvailable){
                //get the samples from database
                val rawSamples= sampleEegDao.getSessionSamplesOrderedByTimestamp(limit = SAMPLES_PER_SESSION, offset = i* SAMPLES_PER_SESSION)
                //get label for the current session, get the label of the last sample

                val labelIndex = rawSamples[0].tiredness - 1 // Convert 1-4 to 0-3 index
                if (labelIndex < 0 || labelIndex > 3) {
                    Log.w(TAG, "Skipping session with invalid label: ${rawSamples[0].tiredness}")
                    continue
                }

                // Create a one-hot encoded array
                val yTrain = FloatArray(4) { 0f }
                yTrain[labelIndex] = 1f
                // Segment the 18,000 samples into 100 chunks of 180 samples each
                val miniSessions = rawSamples.chunked(SAMPLES_PER_MINI_SESSION)
                //extract features from the session samples

                miniSessions.forEachIndexed { chunkIndex, miniSessionSamples ->
                    // Ensure the chunk is the correct size before processing
                    if (miniSessionSamples.size == SAMPLES_PER_MINI_SESSION) {
                        // Extract features from the current 180-sample chunk
                        val featuresMatrix = repository.getFeaturesMatrixSessionSamples(miniSessionSamples)

                        if (featuresMatrix.any { it.isEmpty() }) {
                            Log.e(TAG, "Feature matrix for chunk $chunkIndex in session $i was empty. Skipping.")
                            return@forEachIndexed // Skips to the next iteration of the loop
                        }

                        // Flatten the feature matrix
                        val xTrain = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)

                        // Prepare the inputs for the model's 'train' signature.
                        val trainInputs = mapOf(
                            "x" to arrayOf(xTrain),
                            "y" to arrayOf(yTrain)
                        )

                        val trainOutputs = mutableMapOf<String, Any>()

                        // Run one training step on the current 1.8-second chunk
                        interpreter.runSignature(trainInputs, trainOutputs, "train")
                    } else {
                        Log.w(TAG, "Skipping a mini-session chunk with incorrect size: ${miniSessionSamples.size}")
                    }
                }
            }

            // Export the trained weights as a checkpoint file.
            val outputFile = File(filesDir, "checkpoint.ckpt")
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter.runSignature(inputs, outputs, "save")
            Log.d(TAG, "Successfully saved fine-tuned model to ${outputFile.absolutePath}")

            //deleting the data in the database, not useful anymore <-- TEMPORARILY DISABLED
            /*if(sampleEegDao.deleteAllData()<=0){
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FineTuningService, "Error occurred deleting the database data", Toast.LENGTH_SHORT).show()
                }
                stopSelf()
                return
            }*/
            withContext(Dispatchers.Main) {
                notificationHelper.showNotification(notificationHelper.createFineTuningSuccessNotification(), FineTuningNotification.NOTIFICATION_ID+1)
            }

        } catch (e: Exception) {
            notificationHelper.showNotification(notificationHelper.createGenericErrorNotification(), FineTuningNotification.NOTIFICATION_ID+2)
            stopSelf()
            return
        }
    }

    private fun loadModelFromFile(modelFilename: String): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd(modelFilename)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
        }
    }

}
