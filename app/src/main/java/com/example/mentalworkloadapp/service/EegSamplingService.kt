package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.notification.EegSamplingNotification
import com.example.mentalworkloadapp.util.MentalWorkloadProcessor
import kotlinx.coroutines.*
import mylibrary.mindrove.ServerManager
import mylibrary.mindrove.SensorData
import kotlinx.coroutines.sync.withLock

class EegSamplingService : Service() {
    companion object {
        var isRunning = false
        private const val BATCH_INSERT_SIZE = 250 // 0.5 seconds of data at 500Hz
    }

    private lateinit var networkCheckHandler: Handler
    private lateinit var networkCheckRunnable: Runnable
    private var isServerManagerActive = false
    private var mentalWorkloadProcessor: MentalWorkloadProcessor? = null
    private var inferenceStarted = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var eegDao: SampleEegDAO
    private val eegSampleBuffer = mutableListOf<SampleEeg>()

    // Changed to a nullable var to allow re-instantiation
    private var serverManager: ServerManager? = null

    @Volatile
    private var lastSampling: Long = 0

    override fun onCreate() {
        super.onCreate()
        eegDao = DatabaseProvider.getSampleEegDao(this)

        networkCheckHandler = Handler(Looper.getMainLooper())
        networkCheckRunnable = Runnable {
            manageServerConnection()
            // Repeat this check periodically
            networkCheckHandler.postDelayed(networkCheckRunnable, 5000) // Check every 5 seconds
        }
        EegSamplingNotification(this).createNotificationChannel()
        mentalWorkloadProcessor = MentalWorkloadProcessor(context = this, intervalSeconds = 1L)
        Log.d("EegService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EegService", "Service starting...")
        startForeground(EegSamplingNotification.NOTIF_ID, EegSamplingNotification(this).createNotification())
        isRunning = true
        lastSampling = System.currentTimeMillis()
        // Start the periodic checks
        networkCheckHandler.post(networkCheckRunnable)
        return START_STICKY
    }

    private fun initializeAndStartServer() {
        // Ensure any old server is stopped before creating a new one.
        serverManager?.stop()

        Log.d("EegNetworkService", "Initializing a new ServerManager instance.")
        serverManager = ServerManager { sensorData: SensorData ->
            // This callback is on a background thread from the SDK.
            // Update the timestamp to signal we are receiving data.
            lastSampling = System.currentTimeMillis()

            val sampleEeg = SampleEeg.fromSensorData(sensorData, getCurrentSessionId())

            // Add to buffer instead of immediate save
            synchronized(eegSampleBuffer) {
                eegSampleBuffer.add(sampleEeg)
                // When buffer is full, trigger a batch save
                if (eegSampleBuffer.size >= BATCH_INSERT_SIZE) {
                    val samplesToSave = ArrayList(eegSampleBuffer)
                    eegSampleBuffer.clear()
                    saveToDatabase(samplesToSave)
                }
            }
        }

        try {
            serverManager?.start()
            isServerManagerActive = true
            lastSampling = System.currentTimeMillis() // Reset timer
            Log.i("EegNetworkService", "New ServerManager instance started successfully.")
        } catch (e: Exception) {
            Log.e("EegNetworkService", "Error starting new ServerManager instance", e)
            isServerManagerActive = false
            EegSamplingNotification(this).showWifiErrorNotification()
        }
    }

    private fun manageServerConnection() {
        val timeSinceLastSample = System.currentTimeMillis() - lastSampling

        // If the server is not active or we haven't received data for 5 seconds, try to (re)start it.
        if (!isServerManagerActive || timeSinceLastSample > 5000) {
            if (timeSinceLastSample > 5000) {
                Log.w("EegNetworkService", "No data received for over 5 seconds. Re-initializing server...")
            } else {
                Log.i("EegNetworkService", "Server is not active. Initializing...")
            }

            // Stop inference if it was running
            if (inferenceStarted) {
                mentalWorkloadProcessor?.stop()
                inferenceStarted = false
                Log.i("EegNetworkService", "Inference stopped due to connection issue.")
            }

            // Re-initialize the server. This will stop the old one and start a new one.
            initializeAndStartServer()

        } else {
            // Connection is OK and data is flowing.
            Log.d("EegNetworkService", "Connection OK. Time since last sample: ${timeSinceLastSample}ms")

            // Start the inference processor if it hasn't been started yet
            if (!inferenceStarted) {
                // We set inferenceStarted to true immediately to prevent this block from running again.
                inferenceStarted = true
                serviceScope.launch {
                    Log.i("EegNetworkService", "Sufficient data being received. Starting inference processor in 35 seconds.")
                    delay(35000) // Wait for a stable amount of data to be collected
                    if (isServerManagerActive) { // Double-check connection is still active after the delay
                        mentalWorkloadProcessor?.start()
                        Log.i("EegNetworkService", "MentalWorkloadProcessor started.")
                    } else {
                        Log.w("EegNetworkService", "Connection was lost during the 35s wait. Inference not started.")
                        inferenceStarted = false // Reset the flag so it can try again later.
                    }
                }
            }
        }
    }

    private fun saveToDatabase(eegDataBatch: List<SampleEeg>) {
        serviceScope.launch {
            DatabaseProvider.dbMutex.withLock {
                eegDao.insertSamplesEeg(eegDataBatch)
                Log.d("EegService", "Saved a batch of ${eegDataBatch.size} samples.")
            }
        }
    }

    private fun getCurrentSessionId(): Int {
        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        return sharedPref.getInt("session_id", 1)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("EegService", "Service destroying...")
        networkCheckHandler.removeCallbacks(networkCheckRunnable)
        isRunning = false
        if (isServerManagerActive) {
            serverManager?.stop()
            isServerManagerActive = false
        }

        // Save any remaining samples in the buffer
        synchronized(eegSampleBuffer) {
            if (eegSampleBuffer.isNotEmpty()) {
                val remainingSamples = ArrayList(eegSampleBuffer)
                eegSampleBuffer.clear()
                // Use runBlocking here to ensure data is saved before the service fully dies
                runBlocking(serviceScope.coroutineContext) {
                    saveToDatabase(remainingSamples)
                    Log.i("EegService", "Saved ${remainingSamples.size} remaining samples on destroy.")
                }
            }
        }

        serviceScope.cancel()
        mentalWorkloadProcessor?.stop()
        mentalWorkloadProcessor?.close()
    }
}