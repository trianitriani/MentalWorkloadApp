package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.mentalworkloadapp.data.local.db.AppDatabase
import com.example.mentalworkloadapp.data.repository.EegRepository
import com.example.mentalworkloadapp.util.EegFeatureExtractor
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class FineTuningService : Service() {

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
        val sampleEegDao = AppDatabase.getDatabase(this.getSharedPreferences("prefs", Context.MODE_PRIVATE)).sampleEegDao()
        val repository = EegRepository(sampleEegDao)
        val N_SESSIONS = 50

        try {
            val modelFile = loadModelFromFile("trainable_model.tflite")
            val interpreter = Interpreter(modelFile)

            val rawDataset= sampleEegDao.getLastSamplesOrderedByTimestamp()

            val yTrain = Array(N_SESSIONS) { IntArray(1)}

            for (i in 0 until 50){
                yTrain[i]=rawDataset[i].tiredness
            }

            val featuresMatrix = repository.getFeaturesMatrixLast50Samples()
            if (featuresMatrix.isEmpty()) {
                throw IllegalStateException("Features matrix is empty")
            }
            val xTrain = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)

            val trainInputs = mapOf(
                "x" to xTrain,
                "y" to yTrain
            )
            val trainOutputs = mutableMapOf<String, Any>(
                "loss" to FloatArray(1)
            )

            //model training
            interpreter.runSignature(trainInputs, trainOutputs, "train")

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
