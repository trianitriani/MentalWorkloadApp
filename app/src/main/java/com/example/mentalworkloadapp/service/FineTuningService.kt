package com.example.mentalworkloadapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import android.util.Log

class FineTuningService : Service() {

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val py = Python.getInstance()
        val module = py.getModule("hello") // Refers to hello.py
        val message = module.callAttr("get_message").toString()

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // You could trigger more Python code here if needed.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}