package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class EegSamplingService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // here we initialize the connection with the mindrove
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, createNotification())
        startSampling()
        return START_STICKY
    }

    private suspend fun saveToDatabase(eegData: SampleEeg) {
        appDatabase.sampleEegDao().insertSampleEeg(eegData)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}