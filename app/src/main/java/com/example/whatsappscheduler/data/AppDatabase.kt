package com.example.whatsappscheduler.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromStatus(status: ScheduleStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ScheduleStatus = ScheduleStatus.valueOf(value)

    @TypeConverter
    fun fromTargetType(type: TargetType): String = type.name

    @TypeConverter
    fun toTargetType(value: String): TargetType = TargetType.valueOf(value)
}

@Database(
    entities = [ScheduledMessage::class, FrequentTarget::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun frequentTargetDao(): FrequentTargetDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsapp_scheduler.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
