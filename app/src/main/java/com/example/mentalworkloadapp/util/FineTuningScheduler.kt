package com.example.mentalworkloadapp.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mentalworkloadapp.service.FineTuningReceiver
import java.util.*

//To schedule the fine tuning call the following method
//Scheduler.scheduleFineTuningService( context = this, year = 2025, month = Calendar.JUNE, day = 16, hour = 14, minute = 30 )

object Scheduler {

    fun scheduleFineTuningService(context: Context, year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, FineTuningReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("Scheduler", "Exact alarm scheduled for ${calendar.time}")
        } catch (se: SecurityException) {
            Log.e("Scheduler", "SecurityException scheduling exact alarm", se)
            // Optional fallback
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
