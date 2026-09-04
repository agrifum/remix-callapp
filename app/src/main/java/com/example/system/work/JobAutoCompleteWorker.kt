package com.example.system.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CallUppApplication
import com.example.core.model.JobStatus
import com.example.data.repository.JobRepository

/**
 * JobAutoCompleteWorker:
 * Deterministically auto-completes an individual Job when now >= anchor + 24h.
 * Adheres to MASTER_SPEC §18-19:
 * - Eligible only if status is ACTIVE and job is not deleted.
 * - Respects anchor precedence: confirmedStartAt > preliminary date+time > preliminary date (end of day).
 * - Stale past term on reopened job (anchor <= reopenedAt) never triggers auto-completion.
 */
class JobAutoCompleteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_JOB_ID = "key_job_id"
        fun uniqueWorkName(jobId: String): String = "job_auto_complete_$jobId"
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val app = applicationContext as? CallUppApplication ?: return Result.failure()
        val jobRepository = app.container.jobRepository

        val job = jobRepository.getJobByIdSync(jobId) ?: return Result.success()

        // Section 18: Eligible only if ACTIVE and not deleted
        if (job.status != JobStatus.ACTIVE || job.deletedAt != null) {
            return Result.success()
        }

        val anchor = JobRepository.calculateCompletionAnchor(job) ?: return Result.success()

        // Section 19: If job was reopened and anchor belongs to past term, do NOT complete
        if (job.reopenedAt != null && anchor <= job.reopenedAt) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val dayInMillis = 24L * 60 * 60 * 1000

        if (now >= anchor + dayInMillis) {
            jobRepository.completeJob(job.id)
        } else {
            // Re-schedule with remaining delay if executed prematurely
            app.container.jobCompletionScheduler.scheduleCompletion(job)
        }

        return Result.success()
    }
}
