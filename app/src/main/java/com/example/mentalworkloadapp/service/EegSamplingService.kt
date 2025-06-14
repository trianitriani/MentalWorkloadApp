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
import androidx.room.Room
import com.example.mentalworkloadapp.data.local.db.AppDatabase
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.notification.EegSamplingNotification
import com.example.mentalworkloadapp.service.mentalWorkloadProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mylibrary.mindrove.ServerManager
import mylibrary.mindrove.SensorData
import kotlin.math.abs

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
        val sampleEeg = SampleEeg.fromSensorData(sensorData)
        lastSampling = sampleEeg.timestamp
        // analyze the data
        if (isDeviceProbablyOnTable(sampleEeg)) {
            Log.d("MindRoveService", "⚠️ EEG sembra abbandonato")
        }
        else {
            // insert the sample in the database of the user
            //need subsampling
            saveToDatabase(sampleEeg)
            Log.d("MindRoveService", "EEG CH1: $sampleEeg")
        }
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

    /*
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
    }*/

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
                    val sampleEeg = SampleEeg.fromSensorData(sensorData)
                    lastSampling = sampleEeg.timestamp
                    if (isDeviceProbablyOnTable(sampleEeg)) {
                        Log.d("MindRoveService", "⚠️ EEG sembra abbandonato")
                    } else {
                        saveToDatabase(sampleEeg)
                        Log.d("MindRoveService", "EEG CH1: $sampleEeg")
                    }
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

    /*
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
            Log.d("EegNetworkService", ssid)
            return ssid.contains("mindrove", ignoreCase = true)
        }
        return false
    }
    */

    private fun saveToDatabase(eegData: SampleEeg) {

        measurementsCounter++

        if(measurementsCounter % 5 == 0){ //<-- subsampling
            val eegDao = DatabaseProvider.getSampleEegDao(context = this)
            CoroutineScope(Dispatchers.IO).launch {
                eegDao.insertSampleEeg(eegData)
            }
        }
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
        mentalWorkloadProcessor?.stop()
        mentalWorkloadProcessor?.close()
    }
}




