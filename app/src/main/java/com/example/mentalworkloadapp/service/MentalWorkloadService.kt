package com.example.mentalworkloadapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mentalworkloadapp.R
import kotlinx.coroutines.*
import java.util.*

class MentalWorkloadService : Service() {

    // Instance of the processor that runs the mental workload analysis
    private lateinit var processor: MentalWorkloadProcessor
    // Coroutine scope for running background tasks in this service
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Called when the service is created
    override fun onCreate() {
        super.onCreate()
        // Initialize the processor with application context
        processor = MentalWorkloadProcessor(applicationContext)
        // Start the service as a foreground service with a notification
        startForegroundServiceWithNotification()
    }

    // Called every time the service is started with startService()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Launch the processor's start method in a coroutine on the Default dispatcher
        serviceScope.launch {
            processor.start()
        }
        // Ensure the service is restarted if it gets killed by the system
        return START_STICKY
    }

    // Called when the service is destroyed
    override fun onDestroy() {
        super.onDestroy()
        // Stop the processor loop
        processor.stop()
        // Close and release interpreter resources
        processor.close()
        // Cancel all coroutines running in the service scope
        serviceScope.cancel()
    }

    // No binding provided for this service (not a bound service)
    override fun onBind(intent: Intent?): IBinder? = null

    // Create and start the foreground notification required for long-running background work
    private fun startForegroundServiceWithNotification() {
        val channelId = "mental_workload_channel"
        val channelName = "Mental Workload Monitoring"

        // Create notification channel only for Android O (API 26) and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                // Low importance so it doesn't disturb user much
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Build the notification shown in the foreground service
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mental Workload Monitoring")
            .setContentText("The system is analyzing your mental workload.")
            .setSmallIcon(R.drawable.ic_small_notification)
            .build()

        // Start this service as a foreground service with the notification
        startForeground(1, notification)
    }
}
