package com.example.whatsappscheduler.data

import kotlinx.coroutines.flow.Flow

class ScheduleRepository(
    private val dao: ScheduledMessageDao,
    private val frequentDao: FrequentTargetDao
) {
    fun observeAll(): Flow<List<ScheduledMessage>> = dao.observeAll()

    fun observeFrequent(limit: Int = 20): Flow<List<FrequentTarget>> = frequentDao.observeTop(limit)

    fun observeFrequentContacts(limit: Int = 12): Flow<List<FrequentTarget>> =
        frequentDao.observeTopByType(TargetType.CONTACT, limit)

    fun observeFrequentGroups(limit: Int = 12): Flow<List<FrequentTarget>> =
        frequentDao.observeTopByType(TargetType.GROUP, limit)

    suspend fun getById(id: Long): ScheduledMessage? = dao.getById(id)

    suspend fun getActive(): List<ScheduledMessage> = dao.getActive()

    suspend fun create(
        targetType: TargetType,
        phoneE164: String,
        groupName: String,
        displayLabel: String,
        message: String,
        scheduledAtEpochMs: Long
    ): ScheduledMessage {
        val now = System.currentTimeMillis()
        val entity = ScheduledMessage(
            targetType = targetType,
            phoneE164 = phoneE164,
            groupName = groupName,
            displayLabel = displayLabel,
            message = message,
            scheduledAtEpochMs = scheduledAtEpochMs,
            status = ScheduleStatus.PENDING,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val id = dao.insert(entity)
        recordFrequentUse(targetType, phoneE164, groupName, displayLabel)
        return entity.copy(id = id)
    }

    suspend fun recordFrequentUse(
        targetType: TargetType,
        phoneE164: String,
        groupName: String,
        displayLabel: String
    ) {
        val key = when (targetType) {
            TargetType.CONTACT -> phoneE164
            TargetType.GROUP -> groupName.trim()
        }
        if (key.isBlank()) return
        val label = displayLabel.ifBlank { key }
        val existing = frequentDao.find(targetType, key)
        val now = System.currentTimeMillis()
        if (existing == null) {
            frequentDao.insert(
                FrequentTarget(
                    targetType = targetType,
                    targetKey = key,
                    displayLabel = label,
                    useCount = 1,
                    lastUsedAtEpochMs = now
                )
            )
        } else {
            frequentDao.update(
                existing.copy(
                    displayLabel = label,
                    useCount = existing.useCount + 1,
                    lastUsedAtEpochMs = now
                )
            )
        }
    }

    suspend fun removeFrequent(id: Long) = frequentDao.deleteById(id)

    suspend fun update(message: ScheduledMessage) {
        dao.update(message.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun cancel(id: Long): ScheduledMessage? {
        val current = dao.getById(id) ?: return null
        if (current.status == ScheduleStatus.SENT || current.status == ScheduleStatus.CANCELLED) {
            return current
        }
        val cancelled = current.copy(
            status = ScheduleStatus.CANCELLED,
            updatedAtEpochMs = System.currentTimeMillis(),
            completedAtEpochMs = System.currentTimeMillis()
        )
        dao.update(cancelled)
        return cancelled
    }

    suspend fun markWaitingUnlock(id: Long, attemptToken: String): Boolean {
        val current = dao.getById(id) ?: return false
        if (current.status != ScheduleStatus.PENDING && current.status != ScheduleStatus.WAITING_UNLOCK) {
            return false
        }
        dao.update(
            current.copy(
                status = ScheduleStatus.WAITING_UNLOCK,
                attemptToken = attemptToken,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun beginAttempt(id: Long, attemptToken: String): ScheduledMessage? {
        val current = dao.getById(id) ?: return null
        if (current.status != ScheduleStatus.PENDING && current.status != ScheduleStatus.WAITING_UNLOCK) {
            return null
        }
        val started = current.copy(
            status = ScheduleStatus.IN_PROGRESS,
            attemptToken = attemptToken,
            failureReason = null,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.update(started)
        return started
    }

    suspend fun markSent(id: Long, attemptToken: String): Boolean {
        val current = dao.getById(id) ?: return false
        if (current.attemptToken != null && current.attemptToken != attemptToken) {
            return false
        }
        if (current.status == ScheduleStatus.SENT) {
            return true
        }
        dao.update(
            current.copy(
                status = ScheduleStatus.SENT,
                failureReason = null,
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun markFailed(id: Long, attemptToken: String?, reason: String): Boolean {
        val current = dao.getById(id) ?: return false
        if (attemptToken != null && current.attemptToken != null && current.attemptToken != attemptToken) {
            return false
        }
        if (current.status == ScheduleStatus.SENT || current.status == ScheduleStatus.CANCELLED) {
            return false
        }
        dao.update(
            current.copy(
                status = ScheduleStatus.FAILED,
                failureReason = reason,
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
