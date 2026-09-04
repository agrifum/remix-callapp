package com.example

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.di.AppContainer
import com.example.system.work.JobStatusReconciler
import com.example.system.work.TrashCleanupWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CallUppApplication : Application() {

    lateinit var container: AppContainer
        private set

    lateinit var callStateMonitor: com.example.system.calls.CallStateMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        callStateMonitor = com.example.system.calls.CallStateMonitor(this)
        ensureCallStateMonitoring()
        setupBackgroundWorkers()
        recoverOutstandingTriggers()
    }

    private fun recoverOutstandingTriggers() {
        container.appScope.launch {
            try {
                container.smsTriggerRecovery.recoverPendingTriggers()
            } catch (_: Exception) {
                // Non-blocking safe recovery
            }
        }
    }

    /**
     * Idempotently ensures that call state monitoring via TelephonyCallback is started.
     */
    fun ensureCallStateMonitoring() {
        try {
            if (::callStateMonitor.isInitialized) {
                callStateMonitor.start()
            }
        } catch (_: Exception) {
            // Non-blocking safe fallback
        }
    }

    private fun setupBackgroundWorkers() {
        try {
            val workManager = WorkManager.getInstance(this)

            // Periodic Trash Cleanup (every 24 hours)
            val trashCleanupRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(24, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "CallUppTrashCleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                trashCleanupRequest
            )

            // Periodic Job Status Reconciler (every 6 hours)
            val jobReconcilerRequest = PeriodicWorkRequestBuilder<JobStatusReconciler>(6, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "CallUppJobStatusReconciler",
                ExistingPeriodicWorkPolicy.KEEP,
                jobReconcilerRequest
            )
        } catch (_: Exception) {
            // Safe fallback if WorkManager initialization is delayed
        }
    }
}
