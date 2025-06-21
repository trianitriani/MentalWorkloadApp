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
import com.example.mentalworkloadapp.util.checkPointFileExists
import com.example.mentalworkloadapp.util.restoreModelFromCheckpointFile


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
            //check if checkpoint file exists
            if(checkPointFileExists()){
                //load personalized model
                restoreModelFromCheckpointFile(this,interpreter)
            }

            //getting the number of session available
            val samplesAvailable=sampleEegDao.countSamples()
            val sessionsAvailable:Int= (samplesAvailable/18000L).toInt()
            // Checking if there is enough data
            if (sessionsAvailable < 20) {
                withContext(Dispatchers.Main) {
                    notificationHelper.showNotification(notificationHelper.createNotEnoughDataErrorNotification(20-sessionsAvailable), FineTuningNotification.NOTIFICATION_ID+2)
                }
                stopSelf() // Stop the service if not enough data
                return
            }

            //for each session
            for (i in 0 until sessionsAvailable){
                //get the samples from database
                val rawSamples= sampleEegDao.getSessionSamplesOrderedByTimestamp(limit = 18000, offset = i*18000)
                //get label for the current session, get the label of the last sample
                // in the session
                val yTrain = rawSamples[0].tiredness.toFloat()
                //extract features from the session samples
                val featuresMatrix = repository.getFeaturesMatrixSessionSamples(rawSamples)
                if (featuresMatrix.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        notificationHelper.showNotification(notificationHelper.createGenericErrorNotification(), FineTuningNotification.NOTIFICATION_ID+2)
                    }
                    stopSelf()
                    return
                }
                //flatten the feature matrix
                val xTrain = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)
                //create data structure to pass to model
                val trainInputs = mapOf(
                    "x" to xTrain,
                    "y" to yTrain
                )
                val trainOutputs = mutableMapOf<String, Any>(
                    "loss" to FloatArray(1)
                )
                //run a training stage
                interpreter.runSignature(trainInputs, trainOutputs, "train")
            }


            // Export the trained weights as a checkpoint file.
            val outputFile = File(filesDir, "checkpoint.ckpt")
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter.runSignature(inputs, outputs, "save")

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
