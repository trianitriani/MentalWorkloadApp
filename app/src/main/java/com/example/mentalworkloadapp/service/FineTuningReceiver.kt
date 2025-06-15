package com.example.mentalworkloadapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FineTuningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, FineTuningService::class.java)
        context.startService(serviceIntent)
    }
}
