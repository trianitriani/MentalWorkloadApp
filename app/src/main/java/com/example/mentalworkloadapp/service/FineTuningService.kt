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
                isRunning = true
                Log.d("FineTuningService", "Starting fine tuning")
                fineTuneModel()
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
        isRunning = false
        serviceJob.cancel() // cancel all coroutines when service is destroyed
        super.onDestroy()
    }

    private suspend fun fineTuneModel() {
        val sampleEegDao = DatabaseProvider.getDatabase(this).sampleEegDao()
        val repository = EegRepository(sampleEegDao)
        val N_SESSIONS = 50

        try {
            val modelFile = loadModelFromFile("trainable_model.tflite")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Model loaded", Toast.LENGTH_SHORT).show()
            }
            delay(2000)
            val interpreter = Interpreter(modelFile)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Got interpreter", Toast.LENGTH_SHORT).show()
            }
            val rawDataset= sampleEegDao.getLastSamplesOrderedByTimestamp()
            delay(2000)
            // Checking if there is enough data
            if (rawDataset.size < N_SESSIONS) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FineTuningService, "Not enough data found", Toast.LENGTH_SHORT).show()
                }
                stopSelf() // Stop the service if not enough data
                return
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Enough data", Toast.LENGTH_SHORT).show()
            }

            val yTrain = Array(N_SESSIONS) { IntArray(1)}

            for (i in 0 until 50){
                yTrain[i][0] = rawDataset[i].tiredness
            }

            val featuresMatrix = repository.getFeaturesMatrixLast50Samples()
            if (featuresMatrix.isEmpty()) {
                throw IllegalStateException("Features matrix is empty")
            }
            val xTrain = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Feature", Toast.LENGTH_SHORT).show()
            }
            val trainInputs = mapOf(
                "x" to xTrain,
                "y" to yTrain
            )
            val trainOutputs = mutableMapOf<String, Any>(
                "loss" to FloatArray(1)
            )

            //model training
            interpreter.runSignature(trainInputs, trainOutputs, "train")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Train finished", Toast.LENGTH_SHORT).show()
            }
            //val loss = (trainOutputs["loss"] as FloatArray)[0]
            //Log.d("ModelSignature", "Loss: $loss")



            // Export the trained weights as a checkpoint file.
            val outputFile = File(filesDir, "checkpoint.ckpt")
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter.runSignature(inputs, outputs, "save")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Model saved", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("ModelSignature", "Error during training/inference", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FineTuningService, "Error occurred", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadModelFromFile(modelFilename: String): MappedByteBuffer {

        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        // Get channel to map the file
        val fileChannel = inputStream.channel
        return fileChannel.map(
            // Read-only mode
            FileChannel.MapMode.READ_ONLY,
            // Starting offset in the file
            fileDescriptor.startOffset,
            // Length to read
            fileDescriptor.declaredLength
        )
    }

    private fun restoreModelFromCheckpointFile(checkPointFileName: String,interpreter:Interpreter){
        val outputFile = File(filesDir, checkPointFileName)
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outputFile.absolutePath
        val outputs: Map<String, Any> = HashMap()
        interpreter.runSignature(inputs, outputs, "restore")
    }

}
