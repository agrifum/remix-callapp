package com.example.characterization

import com.example.data.entity.JobEntity
import com.example.system.work.JobCompletionScheduler
import com.example.data.repository.JobRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class JobAutoCompleteSchedulingTest {

    private class RecordingScheduler : JobCompletionScheduler {
        val scheduled = mutableListOf<Pair<String, Long>>() // jobId to delayMs
        val cancelled = mutableListOf<String>()

        override fun scheduleCompletion(job: JobEntity) {
            val anchor = JobRepository.calculateCompletionAnchor(job) ?: return
            val now = System.currentTimeMillis()
            val target = anchor + 24 * 60 * 60 * 1000L
            val delay = (target - now).coerceAtLeast(0L)
            scheduled.add(job.id to delay)
        }

        override fun cancelCompletion(jobId: String) {
            cancelled.add(jobId)
        }
    }

    @Test
    fun calculateCompletionAnchor_prefersConfirmedStartAt() {
        val confirmedTime = 1700000000000L
        val preliminaryDate = LocalDate.of(2026, 9, 5)
        val job = JobEntity(
            id = "job-1",
            clientId = "client-10",
            confirmedStartAt = confirmedTime,
            preliminaryDateEpochDay = preliminaryDate.toEpochDay(),
            preliminaryTimeMinute = 10 * 60
        )

        val anchor = JobRepository.calculateCompletionAnchor(job)
        assertEquals(confirmedTime, anchor)
    }

    @Test
    fun calculateCompletionAnchor_usesPreliminaryDateTimeWhenConfirmedMissing() {
        val zone = ZoneId.systemDefault()
        val preliminaryDate = LocalDate.of(2026, 9, 5)
        val preliminaryTime = LocalTime.of(14, 30)
        val expected = preliminaryDate.atTime(preliminaryTime).atZone(zone).toInstant().toEpochMilli()

        val job = JobEntity(
            id = "job-2",
            clientId = "client-10",
            confirmedStartAt = null,
            preliminaryDateEpochDay = preliminaryDate.toEpochDay(),
            preliminaryTimeMinute = 14 * 60 + 30
        )

        val anchor = JobRepository.calculateCompletionAnchor(job)
        assertEquals(expected, anchor)
    }

    @Test
    fun calculateCompletionAnchor_usesEndOfDayWhenOnlyPreliminaryDatePresent() {
        val zone = ZoneId.systemDefault()
        val preliminaryDate = LocalDate.of(2026, 9, 5)
        val expected = preliminaryDate.atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant().toEpochMilli()

        val job = JobEntity(
            id = "job-3",
            clientId = "client-10",
            confirmedStartAt = null,
            preliminaryDateEpochDay = preliminaryDate.toEpochDay(),
            preliminaryTimeMinute = null
        )

        val anchor = JobRepository.calculateCompletionAnchor(job)
        assertEquals(expected, anchor)
    }

    @Test
    fun calculateCompletionAnchor_returnsNullWhenNoDatesPresent() {
        val job = JobEntity(
            id = "job-4",
            clientId = "client-10",
            confirmedStartAt = null,
            preliminaryDateEpochDay = null,
            preliminaryTimeMinute = null
        )

        val anchor = JobRepository.calculateCompletionAnchor(job)
        assertNull(anchor)
    }

    @Test
    fun recordingScheduler_recordsSchedulingAndCancellation() {
        val scheduler = RecordingScheduler()
        val confirmedTime = System.currentTimeMillis() + 10000L
        val job = JobEntity(
            id = "job-42",
            clientId = "client-10",
            confirmedStartAt = confirmedTime
        )

        scheduler.scheduleCompletion(job)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals("job-42", scheduler.scheduled[0].first)

        scheduler.cancelCompletion("job-42")
        assertEquals(1, scheduler.cancelled.size)
        assertEquals("job-42", scheduler.cancelled[0])
    }
}