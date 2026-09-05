package com.example.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.example.data.entity.JobAnalysisWindowEntity

@Dao
interface JobAnalysisWindowDao {

    @Query("SELECT * FROM job_analysis_windows WHERE jobId = :jobId AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenWindowForJob(jobId: String): JobAnalysisWindowEntity?

    @Query("SELECT * FROM job_analysis_windows WHERE jobId IN (:jobIds) AND endedAt IS NULL")
    suspend fun getOpenWindowsForJobs(jobIds: List<String>): List<JobAnalysisWindowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWindow(window: JobAnalysisWindowEntity)

    @Update
    suspend fun updateWindow(window: JobAnalysisWindowEntity)

    @Query("UPDATE job_analysis_windows SET endedAt = :endedAt WHERE id = :windowId")
    suspend fun closeWindow(windowId: String, endedAt: Long = System.currentTimeMillis())

    @Query("UPDATE job_analysis_windows SET endedAt = :endedAt WHERE jobId = :jobId AND endedAt IS NULL")
    suspend fun closeAllWindowsForJob(jobId: String, endedAt: Long = System.currentTimeMillis())

    @Query("UPDATE job_analysis_windows SET lastAnalyzedSmsAt = :timestamp WHERE id = :windowId")
    suspend fun updateLastAnalyzedSms(windowId: String, timestamp: Long)
}
