package com.example.system.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CallUppApplication

/**
 * TrashCleanupWorker: Cleans up soft-deleted items (notes, tasks, jobs)
 * that have been in the trash for longer than 30 days.
 */
class TrashCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CallUppApplication ?: return Result.failure()
        val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

        return try {
            app.container.noteDao.purgeDeletedNotesOlderThan(cutoffTime)
            app.container.taskDao.purgeDeletedTasksOlderThan(cutoffTime)
            app.container.jobDao.purgeDeletedJobsOlderThan(cutoffTime)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
