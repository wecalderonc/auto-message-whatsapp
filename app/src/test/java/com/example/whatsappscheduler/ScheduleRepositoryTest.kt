package com.example.whatsappscheduler

import com.example.whatsappscheduler.data.ScheduleRepository
import com.example.whatsappscheduler.data.ScheduleStatus
import com.example.whatsappscheduler.data.ScheduledMessage
import com.example.whatsappscheduler.data.ScheduledMessageDao
import com.example.whatsappscheduler.data.FrequentTarget
import com.example.whatsappscheduler.data.FrequentTargetDao
import com.example.whatsappscheduler.data.TargetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryTest {
    private val dao = FakeDao()
    private val frequentDao = FakeFrequentDao()
    private val repo = ScheduleRepository(dao, frequentDao)

    @Test
    fun createStartsPendingAndRecordsFrequent() = runBlocking {
        val created = repo.create(
            targetType = TargetType.CONTACT,
            phoneE164 = "15551234567",
            groupName = "",
            displayLabel = "Ada",
            message = "hello",
            scheduledAtEpochMs = System.currentTimeMillis() + 60_000
        )
        assertEquals(ScheduleStatus.PENDING, created.status)
        assertEquals(1L, created.id)
        assertEquals(1, frequentDao.items.size)
        assertEquals("Ada", frequentDao.items.values.first().displayLabel)
    }

    @Test
    fun createGroupIncrementsFrequentUse() = runBlocking {
        repo.create(
            targetType = TargetType.GROUP,
            phoneE164 = "",
            groupName = "Family",
            displayLabel = "Family",
            message = "hi",
            scheduledAtEpochMs = System.currentTimeMillis() + 60_000
        )
        repo.create(
            targetType = TargetType.GROUP,
            phoneE164 = "",
            groupName = "Family",
            displayLabel = "Family",
            message = "again",
            scheduledAtEpochMs = System.currentTimeMillis() + 120_000
        )
        assertEquals(2, frequentDao.items.values.first().useCount)
    }

    @Test
    fun markSentRejectsMismatchedToken() = runBlocking {
        val created = repo.create(
            targetType = TargetType.CONTACT,
            phoneE164 = "15551234567",
            groupName = "",
            displayLabel = "Ada",
            message = "hello",
            scheduledAtEpochMs = System.currentTimeMillis() + 60_000
        )
        repo.beginAttempt(created.id, "token-a")
        assertFalse(repo.markSent(created.id, "token-b"))
        assertTrue(repo.markSent(created.id, "token-a"))
        assertEquals(ScheduleStatus.SENT, dao.getById(created.id)?.status)
    }

    @Test
    fun cancelDoesNotOverrideSent() = runBlocking {
        val created = repo.create(
            targetType = TargetType.CONTACT,
            phoneE164 = "15551234567",
            groupName = "",
            displayLabel = "Ada",
            message = "hello",
            scheduledAtEpochMs = System.currentTimeMillis() + 60_000
        )
        repo.beginAttempt(created.id, "token-a")
        repo.markSent(created.id, "token-a")
        val result = repo.cancel(created.id)
        assertEquals(ScheduleStatus.SENT, result?.status)
    }

    private class FakeDao : ScheduledMessageDao {
        private val items = linkedMapOf<Long, ScheduledMessage>()
        private var seq = 1L
        private val flow = MutableStateFlow<List<ScheduledMessage>>(emptyList())

        override fun observeAll(): Flow<List<ScheduledMessage>> = flow

        override suspend fun getById(id: Long): ScheduledMessage? = items[id]

        override suspend fun getActive(): List<ScheduledMessage> =
            items.values.filter {
                it.status == ScheduleStatus.PENDING || it.status == ScheduleStatus.WAITING_UNLOCK
            }

        override suspend fun insert(message: ScheduledMessage): Long {
            val id = if (message.id == 0L) seq++ else message.id
            items[id] = message.copy(id = id)
            publish()
            return id
        }

        override suspend fun update(message: ScheduledMessage) {
            items[message.id] = message
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        private fun publish() {
            flow.value = items.values.sortedBy { it.scheduledAtEpochMs }
        }
    }

    private class FakeFrequentDao : FrequentTargetDao {
        val items = linkedMapOf<Long, FrequentTarget>()
        private var seq = 1L
        private val flow = MutableStateFlow<List<FrequentTarget>>(emptyList())

        override fun observeTop(limit: Int): Flow<List<FrequentTarget>> = flow

        override fun observeTopByType(type: TargetType, limit: Int): Flow<List<FrequentTarget>> = flow

        override suspend fun find(type: TargetType, key: String): FrequentTarget? =
            items.values.firstOrNull { it.targetType == type && it.targetKey == key }

        override suspend fun insert(target: FrequentTarget): Long {
            val id = if (target.id == 0L) seq++ else target.id
            items[id] = target.copy(id = id)
            publish()
            return id
        }

        override suspend fun update(target: FrequentTarget) {
            items[target.id] = target
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        private fun publish() {
            flow.value = items.values.sortedByDescending { it.useCount }
        }
    }
}
