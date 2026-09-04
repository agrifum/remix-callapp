package com.example.characterization

import com.example.data.repository.SmsTemplateRepository
import com.example.data.repository.TemplateVariables
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTemplateVariablesCharacterizationTest {

    @Test
    fun fillTemplate_replacesAllPlaceholdersCorrectly() {
        val repo = SmsTemplateRepository(
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

        val templateBody = "Dzień dobry {name}, potwierdzam {service} na dzień {date} o godz. {time} ({address}). Cena: {price}. Dojazd: {arrival_time} ({travel_time})."

        val vars = TemplateVariables(
            name = "Jan Kowalski",
            date = "05.09.2026",
            time = "14:30",
            service = "Dezynsekcja",
            price = "250 zł",
            address = "Warszawa, Puławska 10",
            arrivalTime = "14:25",
            travelTime = "25 min"
        )

        val result = repo.fillTemplate(templateBody, vars)
        val expected = "Dzień dobry Jan Kowalski, potwierdzam Dezynsekcja na dzień 05.09.2026 o godz. 14:30 (Warszawa, Puławska 10). Cena: 250 zł. Dojazd: 14:25 (25 min)."

        assertEquals(expected, result)
    }
}