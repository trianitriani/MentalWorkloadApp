package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
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
        // How many samples to buffer before a batch database write
        private const val BATCH_INSERT_SIZE = 250 // e.g., 0.5 seconds of data at 500Hz
    }

    private lateinit var networkCheckHandler: Handler
    private lateinit var networkCheckRunnable: Runnable
    private var isServerManagerActive = false
    private var mentalWorkloadProcessor: MentalWorkloadProcessor? = null
    private var inferenceStarted = false

    // Coroutine scope for this service
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    //  Hold a single instance of the DAO ---
    private lateinit var eegDao: SampleEegDAO

    // -Buffer for batch inserts ---
    private val eegSampleBuffer = mutableListOf<SampleEeg>()

    // Keep one instance of ServerManager
    private val serverManager = ServerManager { sensorData: SensorData ->
        // This callback is on a background thread from the SDK.
        // Update the timestamp to signal we are receiving data.
        lastSampling = System.currentTimeMillis()

        val sampleEeg = SampleEeg.fromSensorData(sensorData, getCurrentSessionId())

        // Add to buffer instead of immediate save ---
        synchronized(eegSampleBuffer) {
            eegSampleBuffer.add(sampleEeg)
            // When buffer is full, trigger a batch save
            if (eegSampleBuffer.size >= BATCH_INSERT_SIZE) {
                // Copy to a new list to avoid holding the lock during DB operation
                val samplesToSave = ArrayList(eegSampleBuffer)
                eegSampleBuffer.clear()
                saveToDatabase(samplesToSave)
            }
        }
    }

    // Volatile to ensure visibility across threads
    @Volatile
    private var lastSampling: Long = 0

    override fun onCreate() {
        super.onCreate()
        // --- FIX 1 (cont.): Initialize DAO once ---
        eegDao = DatabaseProvider.getSampleEegDao(this)

        networkCheckHandler = Handler(Looper.getMainLooper())
        networkCheckRunnable = Runnable {
            manageServerConnection()
            // repeat this check periodically
            networkCheckHandler.postDelayed(networkCheckRunnable, 2000)
        }
        EegSamplingNotification(this).createNotificationChannel()
        mentalWorkloadProcessor = MentalWorkloadProcessor(context = this, intervalSeconds = 1L)
        Log.d("EegService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EegService", "Service starting...")
        startForeground(EegSamplingNotification.NOTIF_ID, EegSamplingNotification(this).createNotification())
        Log.d("EegService", "startForeground() chiamato fine")
        isRunning = true
        // Initialize lastSampling timestamp to now
        lastSampling = System.currentTimeMillis()
        // start the periodic network check
        networkCheckHandler.post(networkCheckRunnable)
        return START_STICKY
    }

    private fun manageServerConnection() {
        val timeSinceLastSample = System.currentTimeMillis() - lastSampling

        if (timeSinceLastSample > 3000) { // Increased timeout for stability
            Log.w("EegNetworkService", "No data received for a while. Reconnecting... (isServerManagerActive: $isServerManagerActive)")
            // Connection is lost
            if (isServerManagerActive) {
                serverManager.stop()
                isServerManagerActive = false
            }
            if (inferenceStarted) {
                mentalWorkloadProcessor?.stop()
                inferenceStarted = false
            }

            // Try to restart the server
            try {
                serverManager.start()
                isServerManagerActive = true
                lastSampling = System.currentTimeMillis() // Reset timer to avoid immediate re-trigger
                Log.i("EegNetworkService", "ServerManager restarted successfully.")
            } catch (e: Exception) {
                Log.e("EegNetworkService", "Error restarting ServerManager", e)
                EegSamplingNotification(this).showWifiErrorNotification()
                // Don't set isServerManagerActive to true if it fails
                isServerManagerActive = false
            }
        } else {
            // Connection is OK
            if (!isServerManagerActive) {
                // This can happen on first run
                try {
                    serverManager.start()
                    isServerManagerActive = true
                    Log.i("EegNetworkService", "ServerManager started for the first time.")
                } catch (e: Exception) {
                    Log.e("EegNetworkService", "Error starting ServerManager for the first time", e)
                    isServerManagerActive = false
                }
            }

            Log.d("EegNetworkService", "Connection OK.")
            if (!inferenceStarted && isServerManagerActive) {
                // Start inference processor if not already running
                serviceScope.launch {
                    delay(35000) // Wait a bit for data to stabilize
                    if (isServerManagerActive) { // Double check
                        mentalWorkloadProcessor?.start()
                        inferenceStarted = true
                        Log.i("EegNetworkService", "EEG inference processor started.")
                    }
                }
            }
        }
    }

    // Save a batch of samples
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
        // This function can be called from a background thread, but SharedPreferences is thread-safe.
        return sharedPref.getInt("session_id", 1)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("EegService", "Service destroying...")
        networkCheckHandler.removeCallbacks(networkCheckRunnable)
        isRunning = false
        if (isServerManagerActive) {
            // stop the server and clean up resources
            serverManager.stop()
            isServerManagerActive = false
        }

        // Save any remaining samples in the buffer
        synchronized(eegSampleBuffer) {
            if (eegSampleBuffer.isNotEmpty()) {
                val remainingSamples = ArrayList(eegSampleBuffer)
                eegSampleBuffer.clear()
                serviceScope.launch {
                    DatabaseProvider.dbMutex.withLock {
                        saveToDatabase(remainingSamples)
                        Log.i(
                            "EegService",
                            "Saved ${remainingSamples.size} remaining samples on destroy."
                        )
                    }
                }
            }
        }

        // Cancel all coroutines started by this service
        serviceScope.cancel()

        mentalWorkloadProcessor?.stop()
        mentalWorkloadProcessor?.close()
    }
}