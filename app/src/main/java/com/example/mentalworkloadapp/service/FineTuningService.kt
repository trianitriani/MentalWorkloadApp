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


class FineTuningService : Service() {
    companion object {
        var isRunning = false
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                fineTuning()
            } catch (e: Exception) {
                Log.e("FineTuningService", "Fatal error: ${e.message}", e)
                stopSelf()
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel() // cancel all coroutines when service is destroyed
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
                Log.w("FineTuningService", "Session available are less then the treshold. Found ${sessionsAvailable}, threshold is 20")
                val needeedSessions: Int=20-sessionsAvailable
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FineTuningService, "Not enough session recorded, other $needeedSessions needed", Toast.LENGTH_SHORT).show()
                }
                stopSelf() // Stop the service if not enough data
                return
            }


            for (i in 0 until sessionsAvailable){
                //get the samples from database
                val rawSamples= sampleEegDao.getSessionSamplesOrderedByTimestamp(limit = 18000, offset = i*18000)
                //get label for the current session, get the label of the last sample
                // in the session
                val yTrain = rawSamples[0].tiredness.toFloat()
                //extract features from the session samples
                val featuresMatrix = repository.getFeaturesMatrixSessionSamples(rawSamples)
                if (featuresMatrix.isEmpty()) {
                    throw IllegalStateException("Features matrix is empty")
                    stopSelf()
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

            //deleting the data in the database, not usefull anymore <-- TEMPORARY DISABLED
            /*if(sampleEegDao.deleteAllData()<=0){
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FineTuningService, "Error occurred deleting the database data", Toast.LENGTH_SHORT).show()
                }
                stopSelf()
            }*/

            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Model improved correctly!", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Error occurred during improvement", Toast.LENGTH_SHORT).show()
            }
            stopSelf()
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

    private fun restoreModelFromCheckpointFile(checkPointFileName: String,interpreter:Interpreter){
        val outputFile = File(filesDir, checkPointFileName)
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outputFile.absolutePath
        val outputs: Map<String, Any> = HashMap()
        interpreter.runSignature(inputs, outputs, "load_weights")
    }

}
