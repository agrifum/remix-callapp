package com.example.system.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.core.di.AppContainer
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.TriggerState
import com.example.data.entity.SmsTriggerEntity
import kotlinx.coroutines.flow.first

/**
 * SmsTriggerRecovery: Deterministically closes the durability gap between Room trigger insertion
 * and WorkManager enqueueing.
 *
 * Provides:
 * - Immediate enqueue after Room insert.
 * - Startup / process-restart reconciliation for outstanding PENDING/FAILED triggers.
 * - Strict revalidation of global, client, and JobAnalysisWindow eligibility before enqueueing.
 * - Marking triggers DISCARDED when no longer eligible.
 */
class SmsTriggerRecovery(
    private val context: Context,
    private val container: AppContainer
) {

    /**
     * Idempotently enqueues a dedicated SmsAnalysisWorker for an eligible trigger.
     * Re-verifies eligibility (global setting, client, active jobs, open analysis window).
     * If no longer eligible, marks the trigger DISCARDED.
     * Returns true if work was enqueued, false if discarded or ineligible.
     */
    suspend fun enqueueOrDiscard(trigger: SmsTriggerEntity): Boolean {
        // If trigger is already processed or discarded, do nothing
        if (trigger.state == TriggerState.PROCESSED || trigger.state == TriggerState.DISCARDED) {
            return false
        }

        // 1. Check global preference
        val globalEnabled = container.appPreferences.smsAnalysisGlobalEnabled.first()
        if (!globalEnabled) {
            container.smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
            return false
        }

        // 2. Check client and effective mode
        val client = container.clientDao.getClientByIdSync(trigger.clientId)
        if (client == null || client.smsAnalysisMode == SmsAnalysisMode.DISABLED) {
            container.smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
            return false
        }

        // 3. Check active jobs and open window
        val activeJobs = container.jobDao.getActiveJobsForClientSync(client.id)
        val hasEligibleWindow = activeJobs.any { job ->
            val window = container.windowDao.getOpenWindowForJob(job.id)
            window != null && window.endedAt == null && trigger.receivedAt >= window.startedAt
        }
        if (!hasEligibleWindow) {
            container.smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
            return false
        }

        // 4. Deterministic unique work name per trigger
        val workRequest = OneTimeWorkRequestBuilder<SmsAnalysisWorker>()
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to trigger.id))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            getWorkName(trigger.id),
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        return true
    }

    /**
     * Recovers any outstanding PENDING triggers (e.g. after process death or startup).
     * Re-evaluates eligibility and enqueues work idempotently.
     */
    suspend fun recoverPendingTriggers(): Int {
        val pendingTriggers = container.smsTriggerDao.getPendingTriggers()
        var recoveredCount = 0
        for (trigger in pendingTriggers) {
            if (enqueueOrDiscard(trigger)) {
                recoveredCount++
            }
        }
        return recoveredCount
    }

    companion object {
        fun getWorkName(triggerId: String) = "sms_analysis_$triggerId"
    }
}
