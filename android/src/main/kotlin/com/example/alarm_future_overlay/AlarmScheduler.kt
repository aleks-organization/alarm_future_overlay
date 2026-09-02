package com.example.alarm_future_overlay

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

    const val SNOOZE_MILLIS = 10 * 60 * 1000L

    fun schedule(
        context: Context,
        id: Int,
        timeMillis: Long,
        label: String,
        sound: String?,
        volume: Float = 1f
    ): Boolean {
        return try {
            val intent = Intent(context, OverlayAlarmReceiver::class.java).apply {
                putExtra("overlay_id", id)
                putExtra("overlay_time", timeMillis)
                putExtra("alarm_label", label)
                putExtra("alarm_sound", sound)
                putExtra("alarm_volume", volume)
            }
            val pending = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pending)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, timeMillis, pending)
            }
            AlarmStore.save(context, id, timeMillis, label, sound, volume)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun cancel(context: Context, id: Int): Boolean {
        return try {
            val intent = Intent(context, OverlayAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending)
            pending.cancel()
            AlarmStore.remove(context, id)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun snooze(context: Context, id: Int): Boolean {
        cancelNotification(context, id)
        val alarm = AlarmStore.get(context, id) ?: return false
        val newTime = System.currentTimeMillis() + SNOOZE_MILLIS
        return schedule(context, id, newTime, alarm.label, alarm.sound, alarm.volume)
    }

    fun dismiss(context: Context, id: Int): Boolean {
        cancelNotification(context, id)
        return cancel(context, id)
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        for (alarm in AlarmStore.getAll(context)) {
            if (alarm.timeMillis > now) {
                schedule(
                    context,
                    alarm.id,
                    alarm.timeMillis,
                    alarm.label,
                    alarm.sound,
                    alarm.volume
                )
            } else {
                AlarmStore.remove(context, alarm.id)
            }
        }
    }

    private fun cancelNotification(context: Context, id: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
