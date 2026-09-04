package com.example.system.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CallUppApplication
import com.example.core.model.JobStatus

/**
 * JobStatusReconciler:
 * Automatically marks ACTIVE jobs as COMPLETED if 24 hours have passed
 * since their confirmed date/time or preliminary completion anchor.
 */
class JobStatusReconciler(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "CallUppJobStatusReconciler"
        const val UNIQUE_STARTUP_WORK_NAME = "CallUppJobStatusReconcilerStartup"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? CallUppApplication ?: return Result.failure()
        val now = System.currentTimeMillis()
        val dayInMillis = 24L * 60 * 60 * 1000

        return try {
            val activeJobs = app.container.jobRepository.getActiveJobsSync()
            for (job in activeJobs) {
                val anchor = app.container.jobRepository.calculateCompletionAnchor(job) ?: continue
                // Section 19: Stale past term on reopened job must NOT trigger auto-completion
                if (job.reopenedAt != null && anchor <= job.reopenedAt) {
                    continue
                }
                if (now >= anchor + dayInMillis) {
                    app.container.jobRepository.completeJob(job.id)
                } else {
                    // Reconciliation repair: ensure per-job WorkManager worker is enqueued
                    app.container.jobCompletionScheduler.scheduleCompletion(job)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
