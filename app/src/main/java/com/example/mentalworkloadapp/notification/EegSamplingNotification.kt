package com.example.mentalworkloadapp.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.ui.LoaderActivity

class EegSamplingNotification(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "eeg_sampling_channel"
        const val NOTIF_ID = 1
    }

    fun createNotification(): Notification {
        val notificationIntent = Intent(context, LoaderActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher)
        val notification =  NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Selene sampling")
            .setContentText("Selene does tracks your data, with privacy ...")
            .setLargeIcon(largeIconBitmap)
            .setSmallIcon(R.drawable.ic_small_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        return notification
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EEG Sampling Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Canale per notifica servizio campionamento EEG"
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}