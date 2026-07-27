package com.example.whatsappscheduler.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.whatsappscheduler.data.ScheduledMessage

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    fun schedule(message: ScheduledMessage) {
        val manager = alarmManager ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            message.id.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_MESSAGE_ID, message.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = message.scheduledAtEpochMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    fun cancel(messageId: Long) {
        val intent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_MESSAGE_ID, messageId)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager?.cancel(intent)
        intent.cancel()
    }

    companion object {
        const val ACTION_FIRE = "com.example.whatsappscheduler.action.FIRE_SCHEDULE"
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}
