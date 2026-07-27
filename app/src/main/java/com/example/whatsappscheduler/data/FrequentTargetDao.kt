package com.example.whatsappscheduler.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FrequentTargetDao {
    @Query(
        """
        SELECT * FROM frequent_targets
        ORDER BY useCount DESC, lastUsedAtEpochMs DESC
        LIMIT :limit
        """
    )
    fun observeTop(limit: Int = 20): Flow<List<FrequentTarget>>

    @Query(
        """
        SELECT * FROM frequent_targets
        WHERE targetType = :type
        ORDER BY useCount DESC, lastUsedAtEpochMs DESC
        LIMIT :limit
        """
    )
    fun observeTopByType(type: TargetType, limit: Int = 12): Flow<List<FrequentTarget>>

    @Query(
        """
        SELECT * FROM frequent_targets
        WHERE targetType = :type AND targetKey = :key
        LIMIT 1
        """
    )
    suspend fun find(type: TargetType, key: String): FrequentTarget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(target: FrequentTarget): Long

    @Update
    suspend fun update(target: FrequentTarget)

    @Query("DELETE FROM frequent_targets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
