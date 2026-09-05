package com.example

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.system.work.JobStatusReconciler
import com.example.system.work.TrashCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class CallUppApplication : Application() {

    @Inject lateinit var callStateMonitor: com.example.system.calls.CallStateMonitor
    @Inject lateinit var appScope: kotlinx.coroutines.CoroutineScope
    @Inject lateinit var smsTriggerRecovery: com.example.system.work.SmsTriggerRecovery

    override fun onCreate() {
        super.onCreate()
        installProductionAppCheck()
        ensureCallStateMonitoring()
        setupBackgroundWorkers()
        recoverOutstandingTriggers()
    }

    private fun installProductionAppCheck() {
        runCatching {
            FirebaseApp.getInstance()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }

    private fun recoverOutstandingTriggers() {
        appScope.launch {
            try {
                smsTriggerRecovery.recoverPendingTriggers()
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
                JobStatusReconciler.UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                jobReconcilerRequest
            )

            // Startup Job Status Reconciler (§18: run at each app start)
            val startupReconciler = androidx.work.OneTimeWorkRequestBuilder<JobStatusReconciler>()
                .build()
            workManager.enqueueUniqueWork(
                JobStatusReconciler.UNIQUE_STARTUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                startupReconciler
            )
        } catch (_: Exception) {
            // Safe fallback if WorkManager initialization is delayed
        }
    }
}
