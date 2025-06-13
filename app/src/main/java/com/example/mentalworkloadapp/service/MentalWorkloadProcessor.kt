package com.example.mentalworkloadapp.service

import android.content.Context
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

class MentalWorkloadProcessor(
    // Context to access resources and database
    private val context: Context,  
    // Time interval (in seconds) between predictions
    private val intervalSeconds: Long = 1L 
) {
    // Temporary list to accumulate the last 5 predictions
    private val predictions = mutableListOf<Int>() 
    // TensorFlow Lite interpreter to run the model
    private lateinit var interpreter: Interpreter 
    // DAO to save predictions in DB
    private val dao = AppDatabase.getDatabase(context).predictedLevelDao() 

    // Coroutine job managing the inference loop
    private var job: Job? = null  

    // Function to start the periodic inference loop
    fun start() {
        // Initialize the interpreter if not already initialized
        if (::interpreter.isInitialized.not()) {
            interpreter = Interpreter(loadModelFile("4Classes87acc.tflite"))
        }
        // If the job is already active (start was already called), do nothing
        if (job?.isActive == true) return

        // Launch a new coroutine in the background (IO dispatcher)
        job = CoroutineScope(Dispatchers.IO).launch {
            // Infinite loop as long as coroutine is active
            while (isActive) {
                // Get the feature vector extracted from the EEG signal
                val inputVector = EegFeatureExtractor.extractCurrentFeatureVector()

                // Prepare the input buffer for TensorFlow Lite (4 bytes per float)
                val input = ByteBuffer.allocateDirect(4 * inputVector.size).apply {
                    // Use device native endianness
                    order(ByteOrder.nativeOrder())
                    // Write each float into the buffer
                    inputVector.forEach { putFloat(it) }
                    // Reset buffer position to start (very important)
                    rewind()
                }

                // Output buffer: model outputs 4 class probabilities (float array)
                val output = Array(1) { FloatArray(4) }

                // Run the model with input and output buffers
                interpreter.run(input, output)

                // Find the index of the class with the highest probability
                val predictedClass = output[0].indices.maxByOrNull { output[0][it] } ?: 0

                // Add the current prediction to the list
                predictions.add(predictedClass)

                // When 5 predictions are collected, find the most frequent and save to DB
                if (predictions.size == 5) {
                    val mostFrequent = predictions.groupingBy { it }
                        // Count how many times each class appears
                        .eachCount() 
                        // Find the class with max frequency
                        .maxByOrNull { it.value } 
                        ?.key ?: predictedClass

                    // Current timestamp in milliseconds
                    val timestamp = System.currentTimeMillis() 

                    // Insert the prediction into the Room database
                    dao.insert(PredictedLevelEntity(timestamp = timestamp, livelloStanchezza = mostFrequent))

                    // Clear the list to start collecting the next 5 predictions
                    predictions.clear()
                }

                // Wait for the specified interval (in milliseconds) before next prediction
                delay(intervalSeconds * 1000)
            }
        }
    }

    // Function to stop the inference loop
    fun stop() {
        job?.cancel()  // Cancel the coroutine (stop the loop)
        job = null     // Clear the reference
    }

    // Function to close the interpreter and release resources
    fun close() {
        if (::interpreter.isInitialized) {
            interpreter.close()
        }
    }

    // Function to load the .tflite model file from assets as a MappedByteBuffer
    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
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
}
