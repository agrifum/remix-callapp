package com.example.system.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.di.runtimeDependencies
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.TriggerState
import kotlinx.coroutines.flow.first

/**
 * SmsAnalysisWorker: Dedicated WorkManager CoroutineWorker for deferred SMS AI analysis.
 *
 * Guarantees:
 * - Durable execution across process death.
 * - Re-evaluates global, client, and JobAnalysisWindow eligibility before reading SMS.
 * - Re-reads exactly one intended SMS from the system SMS provider (no arbitrary inbox scanning).
 * - Fails closed if SMS cannot be found or if ambiguity exists.
 * - Authoritative durable retry tracking via persistent SmsTrigger.attemptCount in Room.
 * - Stale retries are safely marked DISCARDED.
 */
class SmsAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TRIGGER_ID = "trigger_id"
        const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        val deps = applicationContext.runtimeDependencies()
        val triggerId = inputData.getString(KEY_TRIGGER_ID) ?: return Result.failure()

        val triggerDao = deps.smsTriggerDao()
        val trigger = triggerDao.getTriggerById(triggerId) ?: return Result.failure()

        // 0. Idempotent check: if trigger is already completed or discarded, succeed immediately
        if (trigger.state == TriggerState.PROCESSED || trigger.state == TriggerState.DISCARDED) {
            return Result.success()
        }

        // 1. Re-check global preference without accessing SMS body
        val globalEnabled = deps.appPreferences().smsAnalysisGlobalEnabled.first()
        if (!globalEnabled) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return Result.success()
        }

        // 2. Re-check client and effective SMS analysis setting
        val client = deps.clientDao().getClientByIdSync(trigger.clientId)
        if (client == null || client.smsAnalysisMode == SmsAnalysisMode.DISABLED) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return Result.success()
        }

        // 3. Re-check ACTIVE jobs and JobAnalysisWindow eligibility
        val activeJobs = deps.jobDao().getActiveJobsForClientSync(client.id)
        val hasEligibleWindow = activeJobs.any { job ->
            val window = deps.jobAnalysisWindowDao().getOpenWindowForJob(job.id)
            window != null && window.endedAt == null && trigger.receivedAt >= window.startedAt
        }
        if (!hasEligibleWindow) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return Result.success()
        }

        // 4. Record processing attempt deterministically in persistent storage
        triggerDao.incrementAttemptCount(triggerId)
        val currentAttempt = trigger.attemptCount + 1

        // 5. Re-read exactly one intended SMS from system provider (two-phase resolution)
        val smsBody = deps.systemSmsReader().readSms(trigger.senderPhoneKey, trigger.receivedAt)
        if (smsBody == null) {
            // Target message not found or ambiguous
            return if (currentAttempt < MAX_RETRIES) {
                triggerDao.updateState(triggerId, TriggerState.FAILED)
                Result.retry()
            } else {
                triggerDao.updateState(triggerId, TriggerState.DISCARDED)
                Result.success()
            }
        }

        // 6. Delegate to SmsAnalysisCoordinator for extraction and safe transactional writes
        val success = deps.smsAnalysisCoordinator().processSmsTrigger(triggerId, smsBody)
        return if (success) {
            Result.success()
        } else {
            val updatedTrigger = triggerDao.getTriggerById(triggerId)
            if (updatedTrigger?.state == TriggerState.FAILED && currentAttempt < MAX_RETRIES) {
                Result.retry()
            } else {
                if (updatedTrigger?.state == TriggerState.FAILED) {
                    triggerDao.updateState(triggerId, TriggerState.DISCARDED)
                }
                Result.success()
            }
        }
    }
}
