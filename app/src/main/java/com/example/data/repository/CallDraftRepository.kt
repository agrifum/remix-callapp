package com.example.data.repository

import androidx.room3.withWriteTransaction
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
import com.example.system.work.JobCompletionScheduler
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
    private val serviceDao: ServiceDao,
    private val jobCompletionScheduler: JobCompletionScheduler? = null
) {

    enum class SessionState {
        IDLE_ALLOWED,
        AUTO_IN_PROGRESS,
        MANUAL_IN_PROGRESS,
        COMMITTED
    }

    private val sessionStates = ConcurrentHashMap<String, SessionState>()
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingManualRequests = ConcurrentHashMap<String, OverlayCommitRequest>()
    private val activeSessionHolders = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    // Bounded history of recently committed session IDs (LRU/FIFO, max 100 entries)
    // Ensures isSessionCommitted remains true even after in-flight session structures are cleaned up.
    private val committedSessionHistory: MutableSet<String> = java.util.Collections.synchronizedSet(
        object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                val added = super.add(element)
                while (size > 100) {
                    val iterator = iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                return added
            }
        }
    )

    private fun getSessionMutex(callSessionId: String): Mutex {
        return sessionMutexes.computeIfAbsent(callSessionId) { Mutex() }
    }

    private inline fun <T> withSessionHolder(callSessionId: String, block: () -> T): T {
        activeSessionHolders.computeIfAbsent(callSessionId) { java.util.concurrent.atomic.AtomicInteger(0) }.incrementAndGet()
        try {
            return block()
        } finally {
            val remaining = activeSessionHolders[callSessionId]?.decrementAndGet() ?: 0
            if (remaining <= 0) {
                activeSessionHolders.remove(callSessionId)
                if (isSessionCommitted(callSessionId)) {
                    sessionMutexes.remove(callSessionId)
                    pendingManualRequests.remove(callSessionId)
                }
            }
        }
    }

    fun markSessionCommitted(callSessionId: String) {
        if (callSessionId.isNotBlank()) {
            sessionStates[callSessionId] = SessionState.COMMITTED
            committedSessionHistory.add(callSessionId)
        }
    }

    fun isSessionCommitted(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        return sessionStates[callSessionId] == SessionState.COMMITTED || committedSessionHistory.contains(callSessionId)
    }

    /**
     * Atomically claims or upgrades session ownership for manual commit.
     * If session is COMMITTED, returns false.
     * If session is AUTO_IN_PROGRESS, upgrades session to MANUAL_IN_PROGRESS and returns true,
     * ensuring explicit user intent (Save / Do zadań) is not dropped.
     * If session is IDLE_ALLOWED or null or already MANUAL_IN_PROGRESS, transitions to MANUAL_IN_PROGRESS and returns true.
     */
    fun tryClaimManualCommit(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        if (committedSessionHistory.contains(callSessionId)) return false
        var claimed = false
        sessionStates.compute(callSessionId) { _, currentState ->
            when (currentState) {
                SessionState.COMMITTED -> {
                    claimed = false
                    SessionState.COMMITTED
                }
                SessionState.AUTO_IN_PROGRESS,
                SessionState.MANUAL_IN_PROGRESS,
                null,
                SessionState.IDLE_ALLOWED -> {
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
        if (committedSessionHistory.contains(callSessionId)) return false
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
        if (committedSessionHistory.contains(callSessionId)) return SessionState.COMMITTED
        return sessionStates[callSessionId] ?: SessionState.IDLE_ALLOWED
    }

    fun releaseSession(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        val committed = isSessionCommitted(callSessionId)
        pendingManualRequests.remove(callSessionId)
        activeSessionHolders.remove(callSessionId)
        sessionMutexes.remove(callSessionId)
        sessionStates.remove(callSessionId)
        return committed
    }

    fun cleanupSession(callSessionId: String): Boolean = releaseSession(callSessionId)

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
        withSessionHolder(callSessionId) {
            val mutex = getSessionMutex(callSessionId)
            mutex.withLock {
                try {
                    // Check if manual commit claimed or registered intent before we acquired lock
                    if (sessionStates[callSessionId] == SessionState.MANUAL_IN_PROGRESS) {
                        val manualReq = pendingManualRequests.remove(callSessionId)
                        if (manualReq != null) {
                            performCommitOverlaySession(manualReq)
                        }
                        return
                    }

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
        withSessionHolder(callSessionId) {
            val mutex = getSessionMutex(callSessionId)
            mutex.withLock {
                try {
                    if (sessionStates[callSessionId] == SessionState.MANUAL_IN_PROGRESS) {
                        val manualReq = pendingManualRequests.remove(callSessionId)
                        if (manualReq != null) {
                            performCommitOverlaySession(manualReq)
                        }
                        return
                    }
                    performCommitDraftOnCallEnd(callSessionId, callDirection, callTime)
                } catch (e: Throwable) {
                    resetSessionStateToIdle(callSessionId)
                    throw e
                }
            }
        }
    }

    private suspend fun performCommitDraftOnCallEnd(
        callSessionId: String,
        callDirection: CallDirection?,
        callTime: Long?
    ) {
        // Double check manual hasn't claimed right before Room transaction
        if (sessionStates[callSessionId] == SessionState.MANUAL_IN_PROGRESS) {
            val manualReq = pendingManualRequests.remove(callSessionId)
            if (manualReq != null) {
                performCommitOverlaySession(manualReq)
            }
            return
        }

        val draft = callDraftDao.getDraftSync(callSessionId)
        if (draft == null) {
            markSessionCommitted(callSessionId)
            return
        }
        if (draft.noteText.isNotBlank()) {
            val key = PhoneNumberNormalizer.normalizeKey(draft.phoneKey)
            database.withWriteTransaction {
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
        // Register manual intent immediately so in-flight auto commit can upgrade
        pendingManualRequests[request.callSessionId] = request

        if (!tryClaimManualCommit(request.callSessionId)) {
            // Already finalized/committed
            pendingManualRequests.remove(request.callSessionId)
            return
        }

        withSessionHolder(request.callSessionId) {
            val mutex = getSessionMutex(request.callSessionId)
            mutex.withLock {
                if (isSessionCommitted(request.callSessionId)) {
                    pendingManualRequests.remove(request.callSessionId)
                    return
                }

                try {
                    performCommitOverlaySession(request)
                } catch (e: Throwable) {
                    resetSessionStateToIdle(request.callSessionId)
                    throw e
                } finally {
                    pendingManualRequests.remove(request.callSessionId)
                }
            }
        }
    }

    private suspend fun performCommitOverlaySession(request: OverlayCommitRequest) {
        if (isSessionCommitted(request.callSessionId)) return
        val key = PhoneNumberNormalizer.normalizeKey(request.phone)
        val now = System.currentTimeMillis()
        val jobsToSchedule = mutableListOf<JobEntity>()

        database.withWriteTransaction {
            var clientId: String? = null
            val existingClient = clientDao.getClientByPhoneKeySync(key)

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
                    val insertResult = clientDao.insertClient(newClient)
                    clientId = if (insertResult == -1L) {
                        clientDao.getClientByPhoneKeySync(key)?.id ?: newClientId
                    } else {
                        newClientId
                    }
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
                jobsToSchedule.add(job)

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
        jobsToSchedule.forEach { jobCompletionScheduler?.scheduleCompletion(it) }
    }
}
