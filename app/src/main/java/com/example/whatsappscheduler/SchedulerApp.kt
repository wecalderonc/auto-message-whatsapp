package com.example.whatsappscheduler

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.whatsappscheduler.data.AppDatabase
import com.example.whatsappscheduler.data.ScheduleRepository
import com.example.whatsappscheduler.scheduling.AlarmScheduler
import com.example.whatsappscheduler.util.CountryPreferences

class SchedulerApp : Application() {
    lateinit var repository: ScheduleRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var countryPreferences: CountryPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        countryPreferences = CountryPreferences(this)
        val db = AppDatabase.get(this)
        repository = ScheduleRepository(db.scheduledMessageDao(), db.frequentTargetDao())
        alarmScheduler = AlarmScheduler(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val unlockChannel = NotificationChannel(
            CHANNEL_UNLOCK,
            "Unlock to send",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Prompts you to unlock the phone so a scheduled WhatsApp message can send"
            setBypassDnd(true)
        }

        val resultChannel = NotificationChannel(
            CHANNEL_RESULTS,
            "Send results",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when a scheduled message succeeds or fails"
        }

        manager.createNotificationChannel(unlockChannel)
        manager.createNotificationChannel(resultChannel)
    }

    companion object {
        const val CHANNEL_UNLOCK = "unlock_to_send"
        const val CHANNEL_RESULTS = "send_results"

        @Volatile
        private var instance: SchedulerApp? = null

        fun get(): SchedulerApp = checkNotNull(instance) { "SchedulerApp not initialized" }
    }
}
