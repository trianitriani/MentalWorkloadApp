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
        val MIN_SESSIONS_REQUIRED = 20
        val N_EPOCHS = 20

        try {

            val modelFile = loadModelFromFile("trainable_model.tflite")
            val interpreter = Interpreter(modelFile)
            //check if checkpoint file exists
            if(checkPointFileExists(this)){
                //load personalized model
                restoreModelFromCheckpointFile(this,interpreter)
            }



            val availableSessions=sampleEegDao.getSessionOrderedById(MIN_SESSIONS_REQUIRED)
            val ssize=availableSessions.size

            // Checking if there is enough data

            if (availableSessions.size < MIN_SESSIONS_REQUIRED) {
                withContext(Dispatchers.Main) {
                    notificationHelper.showNotification(notificationHelper.createNotEnoughDataErrorNotification(MIN_SESSIONS_REQUIRED-availableSessions.size), FineTuningNotification.NOTIFICATION_ID + 3)
                }
                stopSelf() // Stop the service if not enough data
                return
            }

            //for 5 epochs
            for (j in 0  until N_EPOCHS) {
                //for each session
                val sessionOrder = availableSessions.shuffled()
                for (i in sessionOrder) {
                    //get the samples from database, considering last 32 seconds
                    val rawSamples = sampleEegDao.getSessionSamplesById(i)
                    //get label for the current session, get the label of the last sample
                    // in the session
                    val yTrain = rawSamples[0].tiredness.toFloat()
                    //extract features from the session samples
                    val featuresMatrix = repository.getFeaturesMatrixSessionSamples(rawSamples)
                    if (featuresMatrix.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            notificationHelper.showNotification(
                                notificationHelper.createGenericErrorNotification(),
                                FineTuningNotification.NOTIFICATION_ID + 2
                            )
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
            }


            // Export the trained weights as a checkpoint file.
            val outputFile = File(filesDir, "checkpoint.ckpt")
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter.runSignature(inputs, outputs, "save")

            //deleting the data in the database, not useful anymore <-- TEMPORARILY DISABLED
            /*
            for(i in sessionOrder){

            if(sampleEegDao.deleteSessionById(i)<=0){
                Log.e("Fine tuning service","error occured trying to delete session :$i")
            }
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
