package com.example.system.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
    private val appPreferences: com.example.data.preferences.AppPreferences,
    private val clientDao: com.example.data.dao.ClientDao,
    private val jobDao: com.example.data.dao.JobDao,
    private val windowDao: com.example.data.dao.JobAnalysisWindowDao,
    private val smsTriggerDao: com.example.data.dao.SmsTriggerDao
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

        // If trigger reached or exceeded retry limit, discard deterministically
        if (trigger.attemptCount >= SmsAnalysisWorker.MAX_RETRIES) {
            smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
            return false
        }

        // 1. Check global preference
        val globalEnabled = appPreferences.smsAnalysisGlobalEnabled.first()
        if (!globalEnabled) {
            smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
            return false
        }

        // 2. Check client and effective mode
        val client = clientDao.getClientByIdSync(trigger.clientId)
        if (client == null || client.smsAnalysisMode == SmsAnalysisMode.DISABLED) {
            smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
            return false
        }

        // 3. Check active jobs and open window
        val activeJobs = jobDao.getActiveJobsForClientSync(client.id)
        val hasEligibleWindow = activeJobs.any { job ->
            val window = windowDao.getOpenWindowForJob(job.id)
            window != null && window.endedAt == null && trigger.receivedAt >= window.startedAt
        }
        if (!hasEligibleWindow) {
            smsTriggerDao.updateState(trigger.id, TriggerState.DISCARDED)
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
     * Recovers any outstanding PENDING or FAILED triggers (e.g. after process death or startup).
     * Re-evaluates eligibility and enqueues work idempotently.
     */
    suspend fun recoverOutstandingTriggers(): Int {
        val recoverableTriggers = smsTriggerDao.getRecoverableTriggers()
        var recoveredCount = 0
        for (trigger in recoverableTriggers) {
            if (enqueueOrDiscard(trigger)) {
                recoveredCount++
            }
        }
        return recoveredCount
    }

    suspend fun recoverPendingTriggers(): Int = recoverOutstandingTriggers()

    companion object {
        fun getWorkName(triggerId: String) = "sms_analysis_$triggerId"
    }
}
