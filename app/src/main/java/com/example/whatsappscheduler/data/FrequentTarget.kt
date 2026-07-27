package com.example.whatsappscheduler.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "frequent_targets",
    indices = [Index(value = ["targetType", "targetKey"], unique = true)]
)
data class FrequentTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: TargetType,
    /** Phone digits or group name key. */
    val targetKey: String,
    val displayLabel: String,
    val useCount: Int = 1,
    val lastUsedAtEpochMs: Long = System.currentTimeMillis()
)
