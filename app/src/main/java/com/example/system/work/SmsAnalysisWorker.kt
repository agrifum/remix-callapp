package com.example.system.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CallUppApplication
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
 * - Fails closed if SMS cannot be found or if eligibility expired.
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
        val app = applicationContext as? CallUppApplication ?: return Result.failure()
        val triggerId = inputData.getString(KEY_TRIGGER_ID) ?: return Result.failure()

        val triggerDao = app.container.smsTriggerDao
        val trigger = triggerDao.getTriggerById(triggerId) ?: return Result.failure()

        // 0. Idempotent check: if trigger is already completed or discarded, succeed immediately
        if (trigger.state == TriggerState.PROCESSED || trigger.state == TriggerState.DISCARDED) {
            return Result.success()
        }

        // 1. Re-check global preference without accessing SMS body
        val globalEnabled = app.container.appPreferences.smsAnalysisGlobalEnabled.first()
        if (!globalEnabled) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return Result.success()
        }

        // 2. Re-check client and effective SMS analysis setting
        val client = app.container.clientDao.getClientByIdSync(trigger.clientId)
        if (client == null || client.smsAnalysisMode == SmsAnalysisMode.DISABLED) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return Result.success()
        }

        // 3. Re-check ACTIVE jobs and JobAnalysisWindow eligibility
        val activeJobs = app.container.jobDao.getActiveJobsForClientSync(client.id)
        val hasEligibleWindow = activeJobs.any { job ->
            val window = app.container.windowDao.getOpenWindowForJob(job.id)
            window != null && window.endedAt == null && trigger.receivedAt >= window.startedAt
        }
        if (!hasEligibleWindow) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return Result.success()
        }

        // 4. Re-read exactly one intended SMS from system provider
        val smsBody = app.container.systemSmsReader.readSms(trigger.senderPhoneKey, trigger.receivedAt)
        if (smsBody == null) {
            // Target message not found in system SMS provider
            return if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                triggerDao.updateState(triggerId, TriggerState.DISCARDED)
                Result.success()
            }
        }

        // 5. Delegate to SmsAnalysisCoordinator for extraction and safe transactional writes
        val success = app.container.smsAnalysisCoordinator.processSmsTrigger(triggerId, smsBody)
        return if (success) {
            Result.success()
        } else {
            val updatedTrigger = triggerDao.getTriggerById(triggerId)
            if (updatedTrigger?.state == TriggerState.FAILED && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}
