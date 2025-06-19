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
    }

    private lateinit var notificationHelper: FineTuningNotification
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        notificationHelper = FineTuningNotification(this)
        notificationHelper.createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        startForeground(FineTuningNotification.NOTIFICATION_ID, notificationHelper.createFineTuningStartedNotification())
        //when service is started
        serviceScope.launch {
            try {
                //start fine tuning
                fineTuning()
            } catch (e: Exception) {
                //in case of exceptions
                notificationHelper.notify(notificationHelper.createGenericErrorNotification())
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
        stopForeground(STOP_FOREGROUND_DETACH)
        super.onDestroy()
    }

    private suspend fun fineTuning() {
        val sampleEegDao = DatabaseProvider.getDatabase(this).sampleEegDao()
        val repository = EegRepository(sampleEegDao)

        try {
            val modelFile = loadModelFromFile("trainable_model.tflite")
            val interpreter = Interpreter(modelFile)

            //getting the number of session available
            val samplesAvailable=sampleEegDao.countSamples()
            val sessionsAvailable:Int= (samplesAvailable/18000L).toInt()
            // Checking if there is enough data
            if (sessionsAvailable < 20) {
                notificationHelper.notify(
                    notificationHelper.createNotEnoughDataErrorNotification(20 - sessionsAvailable)
                )
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
                    notificationHelper.notify(notificationHelper.createGenericErrorNotification())
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

            notificationHelper.notify(notificationHelper.createFineTuningSuccessNotification())

        } catch (e: Exception) {
            notificationHelper.notify(notificationHelper.createGenericErrorNotification())
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
