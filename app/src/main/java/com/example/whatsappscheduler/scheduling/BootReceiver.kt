package com.example.whatsappscheduler.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.whatsappscheduler.SchedulerApp
import com.example.whatsappscheduler.data.ScheduleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = SchedulerApp.get()
                app.repository.getActive()
                    .filter {
                        it.status == ScheduleStatus.PENDING || it.status == ScheduleStatus.WAITING_UNLOCK
                    }
                    .forEach { message ->
                        if (message.scheduledAtEpochMs <= System.currentTimeMillis()) {
                            SendCoordinator.handleDueMessage(app, message.id)
                        } else {
                            app.alarmScheduler.schedule(message)
                        }
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
