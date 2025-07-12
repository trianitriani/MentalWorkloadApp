package com.example.mentalworkloadapp.util

import android.content.Context
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import com.example.mentalworkloadapp.data.repository.EegRepository
import com.example.mentalworkloadapp.notification.EegSamplingNotification
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.util.Log
import com.example.mentalworkloadapp.R


class MentalWorkloadProcessor(
    // Context to access resources and database
    private val context: Context,  
    // Time interval (in seconds) between predictions
    private val intervalSeconds: Long = 32L
) {
    // Temporary list to accumulate the last predictions
    private val predictionBuffer = mutableListOf<Int>()
    // TensorFlow Lite interpreter to run the model
    private lateinit var interpreter: Interpreter 
    // DAO of row data
    private val sampleEegDao = DatabaseProvider.getDatabase(context).sampleEegDao()
    private val repository = EegRepository(sampleEegDao)
    private val notificationHelper = EegSamplingNotification(context)
    // Coroutine job managing the inference loop
    private var job: Job? = null  

    // Variables for notification
    private val threshold = 2
    var lastNotificationSent: String? = null
    private var firstNotificationSent = false

    // Function to start the periodic inference loop
    fun start() {
        // Initialize the interpreter if not already initialized
        if (::interpreter.isInitialized.not()) {
            //load base model
            interpreter = Interpreter(loadModelFile("model.tflite"))
            //check if checkpoint file exists
            if(checkPointFileExists(context)){
                //load personalized model
                restoreModelFromCheckpointFile(context, interpreter)
            }
        }
        // If the job is already active (start was already called), do nothing
        if (job?.isActive == true) return

        // Launch a new coroutine in the background (IO dispatcher)
        job = CoroutineScope(Dispatchers.IO).launch {
            // Infinite loop as long as coroutine is active
            while (isActive) {
                // Get the feature vector extracted from the EEG signal
                val featuresMatrix = repository.getFeaturesMatrixForLastNSeconds(32)  // last 32 seconds
                if (featuresMatrix.isEmpty()) {
                    Log.d("MentalWorkloadProcessor", "Features matrix empty, i'm wait one second for the next loop.")
                    delay(intervalSeconds * 1000)
                    continue
                }
                val inputVector = EegFeatureExtractor.flattenFeaturesMatrix(featuresMatrix)

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

                // The prediction is saved in the temporary buffer
                predictionBuffer.add(predictedClass)

                // The first notification is checked after 5 predictions
                if (!firstNotificationSent && predictionBuffer.size == 5) {
                    val mostFrequent = predictionBuffer.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: predictedClass
                    if (mostFrequent >= threshold) {
                        sendNotification("fatigue")
                        firstNotificationSent = true
                        predictionBuffer.clear()
                    } else predictionBuffer.removeAt(0)
                }

                // The others notifications are checked every 17 predictions
                else if (firstNotificationSent && predictionBuffer.size == 18) {
                    val mostFrequent = predictionBuffer.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: predictedClass
                    val type = if (mostFrequent >= threshold) "fatigue" else "relaxed"

                    // Avoid sending duplicate 'relaxed' notifications
                    if (type != "relaxed" || lastNotificationSent != "relaxed"){
                        sendNotification(type)
                        predictionBuffer.clear()
                    } else predictionBuffer.removeAt(0)
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

    // Function that sends the notification
    private fun sendNotification(type: String) {
        // Checking if "pause" (checkboxNotification) is set
        val sharedPref = context.getSharedPreferences("SelenePreferences", Context.MODE_PRIVATE)
        val isPauseEnabled = sharedPref.getBoolean("pause", false)

        if (!isPauseEnabled) {
            // If the user doesn't want notifications, it returns without sends anything
            return
        }

        notificationHelper.createNotificationChannel()

        // Text and title selection
        val (title, text) = when (type) {
            "fatigue" -> "Mental Fatigue Alert" to "High mental workload detected for extended period."
            "relaxed" -> "Low Mental Workload Notification" to "Now you are fully rested, go back to work!"
            else -> return // Unknown type
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, EegSamplingNotification.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_small_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        lastNotificationSent = type

        notificationManager.notify(1005, notification)
    }

}
