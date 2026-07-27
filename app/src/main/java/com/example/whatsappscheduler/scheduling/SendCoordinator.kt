package com.example.whatsappscheduler.scheduling

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.whatsappscheduler.MainActivity
import com.example.whatsappscheduler.SchedulerApp
import com.example.whatsappscheduler.automation.SendRequest
import com.example.whatsappscheduler.automation.WhatsAppAccessibilityService
import com.example.whatsappscheduler.data.ScheduleStatus
import com.example.whatsappscheduler.util.PermissionChecks
import java.util.UUID
import kotlinx.coroutines.delay

object SendCoordinator {
    private const val UNLOCK_WAIT_MS = 60 * 60 * 1000L
    private const val UNLOCK_POLL_MS = 1_500L

    @Volatile
    private var busy = false

    suspend fun handleDueMessage(app: SchedulerApp, messageId: Long) {
        if (busy) {
            // Another send is in progress; reschedule shortly to avoid overlap.
            val message = app.repository.getById(messageId) ?: return
            if (message.status == ScheduleStatus.PENDING || message.status == ScheduleStatus.WAITING_UNLOCK) {
                app.alarmScheduler.schedule(
                    message.copy(scheduledAtEpochMs = System.currentTimeMillis() + 15_000L)
                )
            }
            return
        }

        busy = true
        try {
            val message = app.repository.getById(messageId) ?: return
            if (message.status != ScheduleStatus.PENDING && message.status != ScheduleStatus.WAITING_UNLOCK) {
                return
            }

            val snapshot = PermissionChecks.snapshot(app)
            if (!snapshot.whatsAppInstalled) {
                app.repository.markFailed(messageId, null, "WhatsApp is not installed")
                notifyResult(app, messageId, success = false, "WhatsApp is not installed")
                return
            }
            if (!snapshot.accessibilityEnabled) {
                app.repository.markFailed(messageId, null, "Accessibility service is disabled")
                notifyResult(app, messageId, success = false, "Enable Accessibility for this app")
                return
            }

            val attemptToken = message.attemptToken ?: UUID.randomUUID().toString()
            val keyguard = app.getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked == true) {
                app.repository.markWaitingUnlock(messageId, attemptToken)
                wakeScreen(app)
                notifyUnlockRequired(app, messageId)
                val unlocked = waitForUnlock(app, UNLOCK_WAIT_MS)
                if (!unlocked) {
                    app.repository.markFailed(
                        messageId,
                        attemptToken,
                        "Phone stayed locked past the unlock wait window"
                    )
                    notifyResult(app, messageId, success = false, "Unlock timed out — message not sent")
                    return
                }
            }

            val started = app.repository.beginAttempt(messageId, attemptToken) ?: return
            val request = SendRequest(
                messageId = started.id,
                attemptToken = attemptToken,
                targetType = started.targetType,
                phoneDigits = started.phoneE164,
                groupName = started.groupName,
                messageText = started.message,
                whatsAppPackage = snapshot.installedWhatsAppPackage
                    ?: return fail(app, messageId, attemptToken, "WhatsApp package missing")
            )

            val result = WhatsAppAccessibilityService.requestSend(app, request)
            if (result.success) {
                app.repository.markSent(messageId, attemptToken)
                notifyResult(app, messageId, success = true, "Message sent")
            } else {
                fail(app, messageId, attemptToken, result.error ?: "Send failed")
            }
        } finally {
            busy = false
        }
    }

    private suspend fun fail(
        app: SchedulerApp,
        messageId: Long,
        attemptToken: String,
        reason: String
    ) {
        app.repository.markFailed(messageId, attemptToken, reason)
        notifyResult(app, messageId, success = false, reason)
    }

    private suspend fun waitForUnlock(context: Context, timeoutMs: Long): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!keyguard.isKeyguardLocked) return true
            delay(UNLOCK_POLL_MS)
        }
        return !keyguard.isKeyguardLocked
    }

    private fun wakeScreen(context: Context) {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "whatsappscheduler:unlock"
        )
        wakeLock.acquire(10_000L)
        wakeLock.release()
    }

    private fun notifyUnlockRequired(context: Context, messageId: Long) {
        val open = PendingIntent.getActivity(
            context,
            messageId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_FOCUS_MESSAGE_ID, messageId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SchedulerApp.CHANNEL_UNLOCK)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Unlock to send WhatsApp")
            .setContentText("A scheduled message is waiting. Unlock within a few minutes.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setFullScreenIntent(open, true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(UNLOCK_NOTIFICATION_BASE + messageId.toInt(), notification)
    }

    private fun notifyResult(context: Context, messageId: Long, success: Boolean, detail: String) {
        val notification = NotificationCompat.Builder(context, SchedulerApp.CHANNEL_RESULTS)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (success) "WhatsApp message sent" else "WhatsApp send failed")
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(RESULT_NOTIFICATION_BASE + messageId.toInt(), notification)

        // Clear unlock prompt if present.
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(UNLOCK_NOTIFICATION_BASE + messageId.toInt())
    }

    private const val UNLOCK_NOTIFICATION_BASE = 10_000
    private const val RESULT_NOTIFICATION_BASE = 20_000
}
