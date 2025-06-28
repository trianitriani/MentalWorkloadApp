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
import androidx.room.Room
import com.example.mentalworkloadapp.data.local.db.AppDatabase
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.notification.EegSamplingNotification
import com.example.mentalworkloadapp.util.MentalWorkloadProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mylibrary.mindrove.ServerManager
import mylibrary.mindrove.SensorData
import kotlin.math.abs
import kotlinx.coroutines.delay

class EegSamplingService : Service() {
    companion object {
        var isRunning = false
        var lastSampling: Long = 0
    }
    private lateinit var networkCheckHandler: Handler
    private lateinit var networkCheckRunnable: Runnable
    private var isServerManagerActive = false
    var measurementsCounter: Int = 0
    private var mentalWorkloadProcessor: MentalWorkloadProcessor? = null
    private var inferenceStarted = false

    private var serverManager = ServerManager { sensorData: SensorData ->
        // gets current session id
        val sampleEeg = SampleEeg.fromSensorData(sensorData, getCurrentSessionId())
        saveToDatabase(sampleEeg)
    }

    override fun onCreate() {
        super.onCreate()
        // here we initialize the connection with the mindrove
        networkCheckHandler = Handler(Looper.getMainLooper())
        networkCheckRunnable = Runnable {
            isManageServerReachable()
            // repeat this check periodically (2s)
            networkCheckHandler.postDelayed(networkCheckRunnable, 2000)
        }
        EegSamplingNotification(this).createNotificationChannel()
        mentalWorkloadProcessor = MentalWorkloadProcessor(context = this, intervalSeconds = 1L)
        Log.d("EegService", "Service creato")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EegService", "startForeground() chiamato")
        startForeground(EegSamplingNotification.NOTIF_ID, EegSamplingNotification(this).createNotification())
        Log.d("EegService", "startForeground() chiamato fine")
        isRunning = true
        // start the periodic network check
        networkCheckHandler.post(networkCheckRunnable)
        return START_STICKY
    }

    private fun isManageServerReachable() {
        if (System.currentTimeMillis() - lastSampling  > 1500) {
            Log.d("EegNetworkService", "Ultimo sampling vecchio.")
            if (isServerManagerActive) {
                isServerManagerActive = false
                serverManager.stop()
                // Ferma inferenza
                mentalWorkloadProcessor?.stop()
                inferenceStarted = false
            }
            try {
                serverManager.start()
                isServerManagerActive = true
            }  catch (e: Exception){
                Log.e("EegNetworkService", "Errore avviando ServerManager: ${e.message}")
                // i have to re define the object because is broken
                serverManager = ServerManager { sensorData: SensorData ->
                    val sampleEeg = SampleEeg.fromSensorData(sensorData, getCurrentSessionId())
                    saveToDatabase(sampleEeg)
                }
                EegSamplingNotification(this).showWifiErrorNotification()
            }
        } else {
            Log.d("EegNetworkService", "Connesso.")
            if (!inferenceStarted && isServerManagerActive) {
                // Attendi 2 secondi e poi avvia inferenza
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    if (isServerManagerActive) { // conferma che sia ancora connesso
                        mentalWorkloadProcessor?.start()
                        inferenceStarted = true
                        Log.d("EegNetworkService", "Inferenza EEG avviata")
                    }
                }
            }
        }
    }

    private fun saveToDatabase(eegData: SampleEeg) {
        measurementsCounter++
        if(measurementsCounter % 5 == 0){ // <-- subsampling
            val eegDao = DatabaseProvider.getSampleEegDao(context = this)
            CoroutineScope(Dispatchers.IO).launch {
                eegDao.insertSampleEeg(eegData)
            }
        }
    }

    private fun getCurrentSessionId() : Int {
        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        var id = sharedPref.getInt("session_id", -1);
        if(id == -1){
            // variable id does not exists
            val eegDao = DatabaseProvider.getSampleEegDao(context = this)
            CoroutineScope(Dispatchers.IO).launch {
                id = eegDao.getLastSessionId() ?: 1
            }
            // update the shared preferences
            sharedPref.edit() {
                putInt("session_id", id + 1)
            }
            return id + 1
        }
        return id
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // stop network checks
        networkCheckHandler.removeCallbacks(networkCheckRunnable)
        isRunning = false
        if (isServerManagerActive) {
            // stop the server and clean up resources
            serverManager.stop()
            isServerManagerActive = false
        }
        mentalWorkloadProcessor?.stop()
        mentalWorkloadProcessor?.close()
    }
}




