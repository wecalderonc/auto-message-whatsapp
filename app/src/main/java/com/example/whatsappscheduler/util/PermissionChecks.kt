package com.example.whatsappscheduler.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.example.whatsappscheduler.automation.WhatsAppAccessibilityService

object WhatsAppPackages {
    const val CONSUMER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"

    val ALL = listOf(CONSUMER, BUSINESS)
}

data class PermissionSnapshot(
    val whatsAppInstalled: Boolean,
    val accessibilityEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val deviceInteractive: Boolean,
    val keyguardLocked: Boolean,
    val installedWhatsAppPackage: String?
)

object PermissionChecks {
    fun snapshot(context: Context): PermissionSnapshot {
        val installedPackage = installedWhatsAppPackage(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)

        val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

        return PermissionSnapshot(
            whatsAppInstalled = installedPackage != null,
            accessibilityEnabled = isAccessibilityServiceEnabled(context),
            exactAlarmsAllowed = exactAllowed,
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
            deviceInteractive = powerManager?.isInteractive == true,
            keyguardLocked = keyguardManager?.isKeyguardLocked == true,
            installedWhatsAppPackage = installedPackage
        )
    }

    fun installedWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        return WhatsAppPackages.ALL.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = "${context.packageName}/${WhatsAppAccessibilityService::class.java.name}"
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabled.any { info ->
            info.resolveInfo.serviceInfo.let { service ->
                "${service.packageName}/${service.name}" == expected
            }
        } || Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
    }
}
