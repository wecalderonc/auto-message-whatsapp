package com.example.whatsappscheduler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: TargetType = TargetType.CONTACT,
    /** Phone digits for CONTACT; empty for GROUP. */
    val phoneE164: String = "",
    /** Exact WhatsApp group title for GROUP; empty for CONTACT. */
    val groupName: String = "",
    /** User-facing label (contact name or group name). */
    val displayLabel: String = "",
    val message: String,
    val scheduledAtEpochMs: Long,
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val attemptToken: String? = null,
    val failureReason: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null
) {
    fun targetDescription(): String = when (targetType) {
        TargetType.CONTACT -> displayLabel.ifBlank { "+$phoneE164" }
        TargetType.GROUP -> displayLabel.ifBlank { groupName }
    }
}
