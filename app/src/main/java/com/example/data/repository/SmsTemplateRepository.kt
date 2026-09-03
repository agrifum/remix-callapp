package com.example.data.repository

import com.example.data.dao.SmsTemplateDao
import com.example.data.entity.SmsTemplateEntity
import kotlinx.coroutines.flow.Flow

data class TemplateVariables(
    val name: String = "",
    val date: String = "",
    val time: String = "",
    val service: String = "",
    val price: String = "",
    val address: String = "",
    val arrivalTime: String = "",
    val travelTime: String = ""
)

class SmsTemplateRepository(private val templateDao: SmsTemplateDao) {

    val allTemplates: Flow<List<SmsTemplateEntity>> = templateDao.getAllTemplates()
    val activeTemplates: Flow<List<SmsTemplateEntity>> = templateDao.getActiveTemplates()

    fun getTemplateById(id: String): Flow<SmsTemplateEntity?> = templateDao.getTemplateById(id)

    suspend fun insertTemplate(template: SmsTemplateEntity) {
        templateDao.insertTemplate(template.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTemplate(template: SmsTemplateEntity) {
        templateDao.updateTemplate(template.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTemplate(id: String) {
        templateDao.deleteTemplateById(id)
    }

    fun fillTemplate(body: String, vars: TemplateVariables): String {
        return body
            .replace("{name}", vars.name)
            .replace("{date}", vars.date)
            .replace("{time}", vars.time)
            .replace("{service}", vars.service)
            .replace("{price}", vars.price)
            .replace("{address}", vars.address)
            .replace("{arrival_time}", vars.arrivalTime)
            .replace("{travel_time}", vars.travelTime)
    }
}
