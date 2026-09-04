package com.example.characterization

import com.example.core.model.EtaSource
import com.example.core.model.JobStatus
import com.example.core.time.DateTimeFormatters
import com.example.data.entity.JobEntity
import com.example.data.repository.SmsTemplateRepository
import com.example.data.repository.TemplateVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ManualEtaCharacterizationTest {

    @Test
    fun manualEta_calculatesTargetArrivalAndTravelTimeDelta() {
        // Given a current reference time
        val zone = ZoneId.of("Europe/Warsaw")
        val fixedNow = ZonedDateTime.of(2026, 9, 4, 14, 0, 0, 0, zone)
        val currentMillis = fixedNow.toInstant().toEpochMilli()

        // User enters 14:35 as manual arrival time
        val targetHour = 14
        val targetMinute = 35

        var targetDateTime = fixedNow.with(LocalTime.of(targetHour, targetMinute, 0, 0))
        if (targetDateTime.isBefore(fixedNow)) {
            targetDateTime = targetDateTime.plusDays(1)
        }
        val arrivalMillis = targetDateTime.toInstant().toEpochMilli()

        // Verify arrival time is in future by exactly 35 minutes
        val deltaMillis = arrivalMillis - currentMillis
        val travelMinutes = deltaMillis / (60 * 1000)
        assertEquals(35L, travelMinutes)

        // Simulate JobEntity updated with manual ETA
        val job = JobEntity(
            id = "job-eta-1",
            clientId = "c-1",
            status = JobStatus.ACTIVE,
            predictedArrivalAt = arrivalMillis,
            etaSource = EtaSource.MANUAL,
            etaUpdatedAt = currentMillis
        )

        assertEquals(EtaSource.MANUAL, job.etaSource)
        assertEquals(arrivalMillis, job.predictedArrivalAt)

        // Verify formatting and template variable substitution
        val arrivalTimeStr = DateTimeFormatters.formatTime(job.predictedArrivalAt!!)
        val travelTimeStr = "$travelMinutes min"

        assertEquals("14:35", arrivalTimeStr)
        assertEquals("35 min", travelTimeStr)

        val vars = TemplateVariables(
            name = "Klient",
            arrivalTime = arrivalTimeStr,
            travelTime = travelTimeStr
        )

        val templateRepo = SmsTemplateRepository(
            object : com.example.data.dao.SmsTemplateDao {
                override fun getAllTemplates() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.entity.SmsTemplateEntity>())
                override fun getActiveTemplates() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.entity.SmsTemplateEntity>())
                override fun getTemplateById(id: String) = kotlinx.coroutines.flow.flowOf(null)
                override suspend fun insertTemplate(template: com.example.data.entity.SmsTemplateEntity) {}
                override suspend fun insertAllTemplates(templates: List<com.example.data.entity.SmsTemplateEntity>) {}
                override suspend fun updateTemplate(template: com.example.data.entity.SmsTemplateEntity) {}
                override suspend fun deleteTemplateById(id: String) {}
                override suspend fun getTemplateCount(): Int = 0
            }
        )

        val templateBody = "Będę u Pana/Pani o {arrival_time} (dojazd ok. {travel_time})."
        val resolved = templateRepo.fillTemplate(templateBody, vars)
        assertEquals("Będę u Pana/Pani o 14:35 (dojazd ok. 35 min).", resolved)
    }

    @Test
    fun manualEta_rollsOverToNextDayIfTargetTimeIsEarlierThanCurrentTime() {
        val zone = ZoneId.of("Europe/Warsaw")
        val fixedNow = ZonedDateTime.of(2026, 9, 4, 23, 45, 0, 0, zone)

        // User enters 00:30 (past midnight)
        val targetHour = 0
        val targetMinute = 30

        var targetDateTime = fixedNow.with(LocalTime.of(targetHour, targetMinute, 0, 0))
        if (targetDateTime.isBefore(fixedNow)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val arrivalMillis = targetDateTime.toInstant().toEpochMilli()
        val deltaMillis = arrivalMillis - fixedNow.toInstant().toEpochMilli()
        val travelMinutes = deltaMillis / (60 * 1000)

        // Delta between 23:45 and 00:30 next day is 45 minutes
        assertEquals(45L, travelMinutes)
        assertEquals(5, targetDateTime.dayOfMonth) // September 5th
    }
}
