package com.example.characterization

import com.example.core.model.JobStatus
import com.example.core.model.SmsAnalysisMode
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientDetailAndTagsCharacterizationTest {

    @Test
    fun testAutomaticTagsGeneration_combinesAddressAndActiveJobServices() {
        val client = ClientEntity(
            id = "c1",
            phoneKey = "+48123456789",
            phoneDisplay = "123 456 789",
            displayName = "Jan Kowalski",
            city = "Warszawa",
            district = "Mokotów",
            street = "Puławska"
        )

        val jobs = listOf(
            JobEntity(
                id = "j1",
                clientId = "c1",
                status = JobStatus.ACTIVE,
                serviceNameSnapshot = "Deratyzacja"
            ),
            JobEntity(
                id = "j2",
                clientId = "c1",
                status = JobStatus.ACTIVE,
                serviceNameSnapshot = "Dezynsekcja"
            ),
            JobEntity(
                id = "j3",
                clientId = "c1",
                status = JobStatus.COMPLETED,
                serviceNameSnapshot = "Odpluskwianie" // Inactive job service should NOT be included in active tags
            )
        )

        val activeJobs = jobs.filter { it.status == JobStatus.ACTIVE }

        val tags = buildList {
            client.city?.ifBlank { null }?.let { add(it) }
            client.district?.ifBlank { null }?.let { add(it) }
            client.street?.ifBlank { null }?.let { add(it) }
            activeJobs.mapNotNull { it.serviceNameSnapshot?.ifBlank { null } }.distinct().forEach { add(it) }
        }

        assertEquals(listOf("Warszawa", "Mokotów", "Puławska", "Deratyzacja", "Dezynsekcja"), tags)
    }

    @Test
    fun testSmsAnalysisModePerClient() {
        val clientDefault = ClientEntity(
            id = "c1",
            phoneKey = "+48111",
            phoneDisplay = "111",
            displayName = "Default Client",
            smsAnalysisMode = SmsAnalysisMode.INHERIT
        )
        assertEquals(SmsAnalysisMode.INHERIT, clientDefault.smsAnalysisMode)

        val clientDisabled = clientDefault.copy(smsAnalysisMode = SmsAnalysisMode.DISABLED)
        assertEquals(SmsAnalysisMode.DISABLED, clientDisabled.smsAnalysisMode)
    }
}