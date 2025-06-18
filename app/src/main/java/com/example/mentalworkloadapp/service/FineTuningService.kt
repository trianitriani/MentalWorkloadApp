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
                runTrainingAndInference()
            } catch (e: Exception) {
                Log.e("FineTuningService", "Fatal error: ${e.message}", e)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel() // cancel all coroutines when service is destroyed
        super.onDestroy()
    }

    private suspend fun runTrainingAndInference() {
        val sampleEegDao = DatabaseProvider.getDatabase(this).sampleEegDao()
        val repository = EegRepository(sampleEegDao)
        val N_SESSIONS = 5

        try {
            val modelFile = loadModelFromFile("trainable_model.tflite")
            val interpreter = Interpreter(modelFile)

            val rawDataset= sampleEegDao.getSessionSamplesOrderedByTimestamp(limit = 180 * N_SESSIONS, offset = 0)
            Log.w("test", "Found ${rawDataset.size}, need $N_SESSIONS.")
            // Checking if there is enough data
            if (rawDataset.size < N_SESSIONS) {
                Log.w("FineTuningService", "Not enough data to run training. Found ${rawDataset.size}, need $N_SESSIONS.")
                stopSelf() // Stop the service if not enough data
                return
            }


            val yTrainArr = Array(N_SESSIONS) { FloatArray(1)}
            Log.w("test", "Y train created")

            for (i in 0 until 5){
                yTrainArr[i][0] = rawDataset[180*i].tiredness.toFloat()
            }
            Log.w("test", "Y train initialized")
            for (i in 0 until N_SESSIONS){
                val featuresMatrix = repository.getFeaturesMatrixSessionSamples(180,i)
                if (featuresMatrix.isEmpty()) {
                    throw IllegalStateException("Features matrix is empty")
                }
                val xTrain = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)
                val yTrain=yTrainArr[i][0]
                val trainInputs = mapOf(
                    "x" to xTrain,
                    "y" to yTrain
                )
                val trainOutputs = mutableMapOf<String, Any>(
                    "loss" to FloatArray(1)
                )
                interpreter.runSignature(trainInputs, trainOutputs, "train")
                Log.w("test", "Train done")
                stopSelf()
            }
            Log.w("test", "Train done")
            //val loss = (trainOutputs["loss"] as FloatArray)[0]
            //Log.d("ModelSignature", "Loss: $loss")



            // Export the trained weights as a checkpoint file.
            val outputFile = File(filesDir, "checkpoint.ckpt")
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter.runSignature(inputs, outputs, "save")


        } catch (e: Exception) {
            Log.e("ModelSignature", "Error during training/inference", e)
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
        interpreter.runSignature(inputs, outputs, "restore")
    }

}
