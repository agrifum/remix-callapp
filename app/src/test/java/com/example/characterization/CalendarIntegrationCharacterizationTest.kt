package com.example.characterization

import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import com.example.system.calendar.CalendarManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarIntegrationCharacterizationTest {

    private class FakeCalendarManager : CalendarManager {
        var hasPermission: Boolean = true
        val createdEvents = mutableListOf<Pair<String, Long>>() // jobId to eventId
        val updatedEvents = mutableListOf<Triple<Long, String, Long?>>() // eventId, jobId, confirmedStartAt
        val deletedEvents = mutableListOf<Long>()
        private var nextEventId = 100L

        override fun hasCalendarPermission(): Boolean = hasPermission

        override suspend fun createEvent(job: JobEntity, client: ClientEntity?): Long? {
            if (!hasPermission) return null
            val id = nextEventId++
            createdEvents.add(job.id to id)
            return id
        }

        override suspend fun updateEvent(calendarEventId: Long, job: JobEntity, client: ClientEntity?): Boolean {
            if (!hasPermission) return false
            updatedEvents.add(Triple(calendarEventId, job.id, job.confirmedStartAt))
            return true
        }

        override suspend fun deleteEvent(calendarEventId: Long): Boolean {
            if (!hasPermission) return false
            deletedEvents.add(calendarEventId)
            return true
        }
    }

    @Test
    fun createEvent_createsAndReturnsEventIdWhenPermissionGranted() = runBlocking {
        val fake = FakeCalendarManager()
        val job = JobEntity(
            id = "j1",
            clientId = "c1",
            confirmedStartAt = 1700000000000L
        )
        val client = ClientEntity(
            id = "c1",
            phoneKey = "+48123456789",
            phoneDisplay = "123 456 789",
            displayName = "Jan Kowalski"
        )

        val eventId = fake.createEvent(job, client)
        assertNotNull(eventId)
        assertEquals(1, fake.createdEvents.size)
        assertEquals("j1", fake.createdEvents[0].first)
    }

    @Test
    fun createEvent_failsGracefullyWhenNoPermission() = runBlocking {
        val fake = FakeCalendarManager().apply { hasPermission = false }
        val job = JobEntity(
            id = "j2",
            clientId = "c1",
            confirmedStartAt = 1700000000000L
        )

        val eventId = fake.createEvent(job, null)
        assertNull(eventId)
        assertTrue(fake.createdEvents.isEmpty())
    }

    @Test
    fun updateEvent_updatesExistingEvent() = runBlocking {
        val fake = FakeCalendarManager()
        val job = JobEntity(
            id = "j3",
            clientId = "c1",
            confirmedStartAt = 1700000050000L,
            calendarEventId = 555L
        )

        val success = fake.updateEvent(555L, job, null)
        assertTrue(success)
        assertEquals(1, fake.updatedEvents.size)
        assertEquals(555L, fake.updatedEvents[0].first)
        assertEquals(1700000050000L, fake.updatedEvents[0].third)
    }

    @Test
    fun deleteEvent_deletesEventById() = runBlocking {
        val fake = FakeCalendarManager()
        val success = fake.deleteEvent(888L)
        assertTrue(success)
        assertEquals(listOf(888L), fake.deletedEvents)
    }
}