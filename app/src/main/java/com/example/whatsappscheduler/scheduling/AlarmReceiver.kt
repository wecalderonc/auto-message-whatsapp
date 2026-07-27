package com.example.whatsappscheduler.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.whatsappscheduler.SchedulerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmScheduler.ACTION_FIRE) return
        val messageId = intent.getLongExtra(AlarmScheduler.EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SendCoordinator.handleDueMessage(SchedulerApp.get(), messageId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
