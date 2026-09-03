package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.SmsTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTemplateDao {

    @Query("SELECT * FROM sms_templates ORDER BY sortOrder ASC, name ASC")
    fun getAllTemplates(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM sms_templates WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    fun getActiveTemplates(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM sms_templates WHERE id = :id LIMIT 1")
    fun getTemplateById(id: String): Flow<SmsTemplateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: SmsTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemplates(templates: List<SmsTemplateEntity>)

    @Update
    suspend fun updateTemplate(template: SmsTemplateEntity)

    @Query("DELETE FROM sms_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: String)

    @Query("SELECT COUNT(*) FROM sms_templates")
    suspend fun getTemplateCount(): Int
}
