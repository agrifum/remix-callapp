package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.model.CallDirection
import com.example.core.model.JobStatus
import com.example.core.model.NameSource
import com.example.core.model.NoteSource
import com.example.core.model.TaskStatus
import com.example.core.model.TimeQualifier
import com.example.core.model.WindowReason
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.database.CallUppDatabase
import com.example.data.dao.CallDraftDao
import com.example.data.dao.ClientDao
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.JobDao
import com.example.data.dao.NoteDao
import com.example.data.dao.ServiceDao
import com.example.data.dao.TaskDao
import com.example.data.entity.CallDraftEntity
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobAnalysisWindowEntity
import com.example.data.entity.JobEntity
import com.example.data.entity.NoteEntity
import com.example.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class OverlayCommitRequest(
    val callSessionId: String,
    val phone: String,
    val noteText: String,
    val markAsClient: Boolean,
    val clientDisplayName: String? = null,
    val createJob: Boolean,
    val serviceId: String? = null,
    val preliminaryDateEpochDay: Long? = null,
    val preliminaryTimeMinute: Int? = null,
    val createOpenTask: Boolean = false,
    val callDirection: CallDirection? = null,
    val callTimestamp: Long? = null
)

class CallDraftRepository(
    private val database: CallUppDatabase,
    private val callDraftDao: CallDraftDao,
    private val noteDao: NoteDao,
    private val clientDao: ClientDao,
    private val jobDao: JobDao,
    private val windowDao: JobAnalysisWindowDao,
    private val taskDao: TaskDao,
    private val serviceDao: ServiceDao
) {

    enum class SessionState {
        IDLE_ALLOWED,
        AUTO_IN_PROGRESS,
        MANUAL_IN_PROGRESS,
        COMMITTED
    }

    private val sessionStates = ConcurrentHashMap<String, SessionState>()
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

    private fun getSessionMutex(callSessionId: String): Mutex {
        return sessionMutexes.computeIfAbsent(callSessionId) { Mutex() }
    }

    fun markSessionCommitted(callSessionId: String) {
        if (callSessionId.isNotBlank()) {
            sessionStates[callSessionId] = SessionState.COMMITTED
        }
    }

    fun isSessionCommitted(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        return sessionStates[callSessionId] == SessionState.COMMITTED
    }

    /**
     * Atomically tries to claim session ownership for manual commit from IDLE_ALLOWED.
     * Returns true if successfully claimed (or already MANUAL_IN_PROGRESS/COMMITTED by this caller),
     * false if AUTO_IN_PROGRESS or COMMITTED by auto.
     */
    fun tryClaimManualCommit(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        var claimed = false
        sessionStates.compute(callSessionId) { _, currentState ->
            when (currentState) {
                SessionState.COMMITTED -> {
                    claimed = false
                    SessionState.COMMITTED
                }
                SessionState.AUTO_IN_PROGRESS -> {
                    // Auto already claimed. Precedence rule: if auto won transition, do not let manual steal it.
                    claimed = false
                    SessionState.AUTO_IN_PROGRESS
                }
                SessionState.MANUAL_IN_PROGRESS -> {
                    claimed = true
                    SessionState.MANUAL_IN_PROGRESS
                }
                null, SessionState.IDLE_ALLOWED -> {
                    claimed = true
                    SessionState.MANUAL_IN_PROGRESS
                }
            }
        }
        return claimed
    }

    /**
     * Atomically tries to claim session ownership for auto call-end commit from IDLE_ALLOWED.
     * Returns true if successfully transitioned IDLE_ALLOWED -> AUTO_IN_PROGRESS.
     */
    fun tryClaimAutoCommit(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        var claimed = false
        sessionStates.compute(callSessionId) { _, currentState ->
            when (currentState) {
                null, SessionState.IDLE_ALLOWED -> {
                    claimed = true
                    SessionState.AUTO_IN_PROGRESS
                }
                else -> {
                    claimed = false
                    currentState
                }
            }
        }
        return claimed
    }

    fun resetSessionStateToIdle(callSessionId: String) {
        if (callSessionId.isNotBlank()) {
            sessionStates.compute(callSessionId) { _, currentState ->
                if (currentState == SessionState.MANUAL_IN_PROGRESS || currentState == SessionState.AUTO_IN_PROGRESS) {
                    SessionState.IDLE_ALLOWED
                } else {
                    currentState
                }
            }
        }
    }

    fun getSessionState(callSessionId: String): SessionState {
        if (callSessionId.isBlank()) return SessionState.IDLE_ALLOWED
        return sessionStates[callSessionId] ?: SessionState.IDLE_ALLOWED
    }

    fun getDraft(callSessionId: String): Flow<CallDraftEntity?> = callDraftDao.getDraft(callSessionId)

    suspend fun getDraftSync(callSessionId: String): CallDraftEntity? = callDraftDao.getDraftSync(callSessionId)

    suspend fun saveDraft(draft: CallDraftEntity) {
        val key = PhoneNumberNormalizer.normalizeKey(draft.phoneKey)
        callDraftDao.upsertDraft(draft.copy(phoneKey = key, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDraft(callSessionId: String) {
        callDraftDao.deleteDraft(callSessionId)
    }

    /**
     * Atomically flushes any latest in-memory draft from overlay and commits on call end.
     * Guarantees deterministic order: latest draft state -> save/flush -> commit.
     * Prevents race conditions where IDLE occurs within the 500ms debounce window.
     * Synchronized per callSessionId to guarantee idempotency across concurrent IDLE handlers.
     */
    suspend fun flushAndCommitOnCallEnd(
        callSessionId: String,
        latestDraft: CallDraftEntity?,
        callDirection: CallDirection? = null,
        callTime: Long? = null
    ) {
        if (callSessionId.isBlank()) return
        if (!tryClaimAutoCommit(callSessionId)) {
            return
        }
        val mutex = getSessionMutex(callSessionId)
        mutex.withLock {
            try {
                if (latestDraft != null && latestDraft.callSessionId == callSessionId) {
                    saveDraft(latestDraft)
                }
                performCommitDraftOnCallEnd(callSessionId, callDirection, callTime)
            } catch (e: Throwable) {
                resetSessionStateToIdle(callSessionId)
                throw e
            }
        }
    }

    /**
     * Section 13.5: If the call ends without pressing Save and draft note text is non-empty:
     * automatically commit it as a normal Note. Do NOT automatically create Client, Job, or Task.
     * Synchronized per callSessionId to guarantee idempotency.
     */
    suspend fun commitDraftOnCallEnd(callSessionId: String, callDirection: CallDirection? = null, callTime: Long? = null) {
        if (callSessionId.isBlank()) return
        if (!tryClaimAutoCommit(callSessionId)) {
            return
        }
        val mutex = getSessionMutex(callSessionId)
        mutex.withLock {
            try {
                performCommitDraftOnCallEnd(callSessionId, callDirection, callTime)
            } catch (e: Throwable) {
                resetSessionStateToIdle(callSessionId)
                throw e
            }
        }
    }

    private suspend fun performCommitDraftOnCallEnd(
        callSessionId: String,
        callDirection: CallDirection?,
        callTime: Long?
    ) {
        val draft = callDraftDao.getDraftSync(callSessionId)
        if (draft == null) {
            markSessionCommitted(callSessionId)
            return
        }
        if (draft.noteText.isNotBlank()) {
            val key = PhoneNumberNormalizer.normalizeKey(draft.phoneKey)
            database.withTransaction {
                val note = NoteEntity(
                    id = UUID.randomUUID().toString(),
                    phoneKey = key,
                    rawText = draft.noteText.trim(),
                    source = NoteSource.CALL,
                    sourceCallDirection = callDirection,
                    sourceCallAt = callTime ?: System.currentTimeMillis()
                )
                noteDao.insertNote(note)
                callDraftDao.deleteDraft(callSessionId)
            }
            // Mark committed only after the database transaction successfully commits
            markSessionCommitted(callSessionId)
        } else {
            callDraftDao.deleteDraft(callSessionId)
            markSessionCommitted(callSessionId)
        }
    }

    /**
     * Section 13.6 and 32: Transactional commit from overlay (Save or Do Zadań).
     * Synchronized per callSessionId; marks committed only after successful Room transaction.
     */
    suspend fun commitOverlaySession(request: OverlayCommitRequest) {
        if (request.callSessionId.isBlank()) return
        if (!tryClaimManualCommit(request.callSessionId)) {
            // If manual cannot claim (e.g., auto already in progress or committed), return or wait/exit.
            // Requirement: "If auto commit already atomically gained ownership earlier, do not artificially interrupt started Room transaction."
            // But if tryClaimManualCommit returns false because of AUTO_IN_PROGRESS or COMMITTED, manual does not override.
            return
        }

        val mutex = getSessionMutex(request.callSessionId)
        mutex.withLock {
            if (isSessionCommitted(request.callSessionId)) {
                return
            }
            val key = PhoneNumberNormalizer.normalizeKey(request.phone)
            val now = System.currentTimeMillis()

            try {
                callDraftDao.getDraftSync(request.callSessionId)
                database.withTransaction {
                var clientId: String? = null
                var existingClient = clientDao.getClientByPhoneKeySync(key)

                if (request.markAsClient) {
                    if (existingClient == null) {
                        val newClientId = UUID.randomUUID().toString()
                        val display = request.clientDisplayName?.takeIf { it.isNotBlank() }
                            ?: ("Klient " + PhoneNumberNormalizer.formatDisplay(key))
                        val newClient = ClientEntity(
                            id = newClientId,
                            phoneKey = key,
                            phoneDisplay = PhoneNumberNormalizer.formatDisplay(key),
                            displayName = display,
                            nameSource = if (request.clientDisplayName != null) NameSource.CONTACT else NameSource.AUTO,
                            createdAt = now,
                            updatedAt = now
                        )
                        clientDao.insertClient(newClient)
                        clientId = newClientId
                    } else {
                        clientId = existingClient.id
                    }
                } else {
                    clientId = existingClient?.id
                }

                var noteId: String? = null
                if (request.noteText.isNotBlank()) {
                    val nId = UUID.randomUUID().toString()
                    val note = NoteEntity(
                        id = nId,
                        phoneKey = key,
                        rawText = request.noteText.trim(),
                        source = NoteSource.CALL,
                        sourceCallDirection = request.callDirection,
                        sourceCallAt = request.callTimestamp ?: now,
                        createdAt = now,
                        updatedAt = now
                    )
                    noteDao.insertNote(note)
                    noteId = nId

                    if (request.createOpenTask) {
                        val task = TaskEntity(
                            id = UUID.randomUUID().toString(),
                            noteId = nId,
                            status = TaskStatus.OPEN,
                            createdAt = now
                        )
                        taskDao.insertTask(task)
                    }
                }

                if (request.createJob && clientId != null) {
                    val jobId = UUID.randomUUID().toString()
                    var serviceName: String? = null
                    var defaultPrice: Long? = null
                    if (!request.serviceId.isNullOrBlank()) {
                        val service = serviceDao.getServiceByIdSync(request.serviceId)
                        serviceName = service?.name
                        defaultPrice = service?.defaultPriceMinor
                    }

                    val clientSnapshot = clientDao.getClientByIdSync(clientId)
                    val job = JobEntity(
                        id = jobId,
                        clientId = clientId,
                        serviceId = request.serviceId,
                        serviceNameSnapshot = serviceName,
                        priceMinor = defaultPrice,
                        preliminaryDateEpochDay = request.preliminaryDateEpochDay,
                        preliminaryTimeMinute = request.preliminaryTimeMinute,
                        preliminaryTimeQualifier = TimeQualifier.EXACT,
                        addressCitySnapshot = clientSnapshot?.city,
                        addressDistrictSnapshot = clientSnapshot?.district,
                        addressStreetSnapshot = clientSnapshot?.street,
                        addressBuildingSnapshot = clientSnapshot?.buildingNumber,
                        addressUnitSnapshot = clientSnapshot?.unitNumber,
                        addressPostalCodeSnapshot = clientSnapshot?.postalCode,
                        status = JobStatus.ACTIVE,
                        createdAt = now,
                        updatedAt = now
                    )
                    jobDao.insertJob(job)

                    val window = JobAnalysisWindowEntity(
                        jobId = jobId,
                        startedAt = now,
                        reason = WindowReason.CREATED
                    )
                    windowDao.insertWindow(window)
                }

                callDraftDao.deleteDraft(request.callSessionId)
            }

                // Mark committed only after successful transaction completion
                markSessionCommitted(request.callSessionId)
            } catch (e: Throwable) {
                resetSessionStateToIdle(request.callSessionId)
                throw e
            }
        }
    }
}

