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
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.notification.EegSamplingNotification
import mylibrary.mindrove.ServerManager
import mylibrary.mindrove.SensorData
import kotlin.math.abs

class EegSamplingService : Service() {
    companion object {
        var isRunning = false
    }
    private lateinit var networkCheckHandler: Handler
    private lateinit var networkCheckRunnable: Runnable
    private var isServerManagerActive = false
    private val serverManager = ServerManager { sensorData: SensorData ->
        val sampleEeg = SampleEeg.fromSensorData(sensorData)
        Log.d("MindRoveService", "EEG CH1: $sampleEeg")
        // analyze the data
        if (isDeviceProbablyOnTable(sampleEeg)) {
            Log.d("MindRoveService", "⚠️ EEG sembra abbandonato")
        } else {
            Log.d("MindRoveService", "EEG funzionaaaaa!!!")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // here we initialize the connection with the mindrove
        networkCheckHandler = Handler(Looper.getMainLooper())
        networkCheckRunnable = Runnable {
            checkNetworkAndManageServer()
            // repeat this check periodically (5s)
            networkCheckHandler.postDelayed(networkCheckRunnable, 5000)
        }
        EegSamplingNotification(this).createNotificationChannel()
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

    private fun checkNetworkAndManageServer() {
        if (!isNetworkConnected()) {
            // send to the user a notify to connect the device to the wifi of the mindrove
            Log.d("EegNetworkService", "Network NOT available.")
            if(isServerManagerActive){
                // stopping the server manager to send the data
                serverManager.stop()
                isServerManagerActive = false
            }
        } else {
            if (!isServerManagerActive) {
                Log.d("EegNetworkService", "Network available, Starting ServerManager.")
                // starting the server to send the data
                serverManager.start()
                isServerManagerActive = true
            }
        }
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // check if a connection is related to a wifi
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false

        // check if a connection is related to mindrove or not
        val transportInfo = capabilities.transportInfo
        if (transportInfo is WifiInfo) {
            val ssid = transportInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
            return ssid.contains("mindrove", ignoreCase = true)
        }
        return false
    }

    private suspend fun saveToDatabase(eegData: SampleEeg) {
        TODO()
    }

    private fun isDeviceProbablyOnTable(sampleEeg: SampleEeg): Boolean{
        // summarize the total and check if is lower then 5
        val gyroTotal = abs(sampleEeg.angularRateX) +
                abs(sampleEeg.angularRateY) +
                abs(sampleEeg.angularRateZ)
        Log.d("ServiceOnTable", "gyro: $gyroTotal")
        return gyroTotal < 5.00
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
    }
}




