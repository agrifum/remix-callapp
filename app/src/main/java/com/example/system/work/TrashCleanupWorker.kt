package com.example.system.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.di.runtimeDependencies

/**
 * TrashCleanupWorker: Cleans up soft-deleted items (notes, tasks, jobs)
 * that have been in the trash for longer than 30 days.
 */
class TrashCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val deps = applicationContext.runtimeDependencies()
        val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

        return try {
            deps.noteDao().purgeDeletedNotesOlderThan(cutoffTime)
            deps.taskDao().purgeDeletedTasksOlderThan(cutoffTime)
            deps.jobDao().purgeDeletedJobsOlderThan(cutoffTime)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
