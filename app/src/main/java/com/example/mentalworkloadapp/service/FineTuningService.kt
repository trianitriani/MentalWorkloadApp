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
import java.io.FileInputStream
import java.io.File
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
            val modelFile = loadModelFile("trainable_model.tflite")
            val interpreter = Interpreter(modelFile)

            val xTrain = Array(N_SESSIONS) { FloatArray(90) { 0.1f } }
            val yTrain = Array(N_SESSIONS) { IntArray(1)}


            val featuresMatrix = repository.getFeaturesMatrixForLastNSeconds(1)
            if (featuresMatrix.isEmpty()) {
                throw IllegalStateException("Features matrix is empty")
            }
            val inputVector = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)

            val trainInputs = mapOf(
                "x" to xTrain,
                "y" to yTrain
            )
            val trainOutputs = mutableMapOf<String, Any>(
                "loss" to FloatArray(1)
            )

            interpreter.runSignature(trainInputs, trainOutputs, "train")
            val loss = (trainOutputs["loss"] as FloatArray)[0]
            Log.d("ModelSignature", "Loss: $loss")

            // Inference
            val xInfer = Array(1) { FloatArray(90) { 0.1f } }
            val inferInputs = mapOf("x" to xInfer)
            val inferOutputs = mutableMapOf<String, Any>(
                "output" to FloatArray(1)
            )
            interpreter.runSignature(inferInputs, inferOutputs, "infer")
            val prediction = (inferOutputs["output"] as FloatArray)[0]
            Log.d("ModelSignature", "Prediction: $prediction")

            // Save
            val saveInputs = emptyMap<String, Any>()
            val saveOutputs = mutableMapOf<String, Any>(
                "w0" to Array(64) { FloatArray(128) },
                "b0" to FloatArray(64),
                "w1" to Array(5) { FloatArray(64) },
                "b1" to FloatArray(5)
            )

            interpreter.runSignature(saveInputs, saveOutputs, "save")

            val dense2Weights = saveOutputs["w0"] as Array<FloatArray>
            val dense2Bias = saveOutputs["b0"] as FloatArray
            val outputWeights = saveOutputs["w1"] as Array<FloatArray>
            val outputBias = saveOutputs["b1"] as FloatArray

            saveToFile("dense2.w", dense2Weights)
            saveToFile("dense2.b", dense2Bias)
            saveToFile("output.w", outputWeights)
            saveToFile("output.b", outputBias)

        } catch (e: Exception) {
            Log.e("ModelSignature", "Error during training/inference", e)
        }
    }

    private fun loadModelFile(modelFilename: String): MappedByteBuffer {
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

    private fun saveToFile(name: String, array: Any) {
        val file = File(filesDir, name)
        file.printWriter().use { out ->
            when (array) {
                is FloatArray -> array.forEach { out.println(it) }
                is Array<*> -> array.forEach {
                    if (it is FloatArray) {
                        out.println(it.joinToString(","))
                    }
                }
            }
        }
    }
}
