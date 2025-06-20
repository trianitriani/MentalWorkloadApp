package com.example.mentalworkloadapp.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.ui.LoaderActivity

class FineTuningNotification(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "Fine_tuning_channel"
        const val CHANNEL_NAME = "Fine Tuning Service"
        const val CHANNEL_DESCRIPTION = "Channel for fine tuning"
        const val NOTIFICATION_ID = 99
    }

    fun createFineTuningStartedNotification(): Notification {

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_small_notification)
            .setContentTitle("Selene")
            .setContentText("Model improving process : Started")
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
    fun createFineTuningSuccessNotification(): Notification {

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_small_notification)
            .setContentTitle("Selene")
            .setContentText("Model correctly improved")
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun createGenericErrorNotification(): Notification {
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_small_notification)
            .setContentTitle("Selene")
            .setContentText("Model improving process failed")
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun createNotEnoughDataErrorNotification(sessionsNeeded:Int): Notification {
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_small_notification)
            .setContentTitle("Selene")
            .setContentText("Model improving process failed: Not enough data recorded.\nCollect another $sessionsNeeded session")
            .setLargeIcon(largeIcon)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existingChannel = manager.getNotificationChannel(CHANNEL_ID)

            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showNotification(notification: Notification) {
        createNotificationChannel()

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is NOT granted — handle appropriately (e.g., show rationale or request permission)
                Log.w("FineTuningNotification", "Notification permission not granted.")
                return
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun notify(notification: Notification) {
        if (!canPostNotifications()) {
            Log.w("FineTuningNotification", "Notification permission not granted.")
            return
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permissions not required pre-Android 13
        }
    }

}
