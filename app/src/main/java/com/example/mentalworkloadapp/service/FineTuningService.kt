package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FineTuningService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            runTrainingAndInference()
        }.start()
        return START_NOT_STICKY
    }

    private fun runTrainingAndInference() {
        val N_COLUMNS = 10
        try {
            val modelFile = loadModelFile("4Classes87acc.tflite")
            val interpreter = Interpreter(modelFile)

            val xTrain = Array(N_COLUMNS) { FloatArray(90) { 0.1f } } //<--- inputs
            val yTrain = Array(N_COLUMNS) { FloatArray(1) { 1.0f } }  //<-- labels

            //populating datastructures from database


            //

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

            //save
            val saveInputs = emptyMap<String, Any>()
            val saveOutputs = mutableMapOf<String, Any>(
                "w0" to Array(64) { FloatArray(128) },   // dense2 kernel
                "b0" to FloatArray(64),                 // dense2 bias
                "w1" to Array(5) { FloatArray(64) },    // output kernel
                "b1" to FloatArray(5)                   // output bias
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