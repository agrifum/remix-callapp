package com.example.system.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.core.model.JobStatus
import com.example.data.entity.JobEntity
import com.example.data.repository.JobRepository
import java.util.concurrent.TimeUnit

interface JobCompletionScheduler {
    fun scheduleCompletion(job: JobEntity)
    fun cancelCompletion(jobId: String)
}

class WorkManagerJobCompletionScheduler(
    private val context: Context,
    private val workManagerProvider: () -> WorkManager = { WorkManager.getInstance(context) }
) : JobCompletionScheduler {

    override fun scheduleCompletion(job: JobEntity) {
        if (job.status != JobStatus.ACTIVE || job.deletedAt != null) {
            cancelCompletion(job.id)
            return
        }

        val anchor = JobRepository.calculateCompletionAnchor(job)
        if (anchor == null) {
            cancelCompletion(job.id)
            return
        }

        // Section 19: If job was reopened and anchor belongs to past term, do NOT schedule
        if (job.reopenedAt != null && anchor <= job.reopenedAt) {
            cancelCompletion(job.id)
            return
        }

        val targetTime = anchor + 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val delay = maxOf(0L, targetTime - now)

        val request = OneTimeWorkRequestBuilder<JobAutoCompleteWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(JobAutoCompleteWorker.KEY_JOB_ID to job.id))
            .build()

        try {
            workManagerProvider().enqueueUniqueWork(
                JobAutoCompleteWorker.uniqueWorkName(job.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (_: Exception) {
            // Safe fallback if WorkManager not initialized
        }
    }

    override fun cancelCompletion(jobId: String) {
        try {
            workManagerProvider().cancelUniqueWork(JobAutoCompleteWorker.uniqueWorkName(jobId))
        } catch (_: Exception) {
        }
    }
}
