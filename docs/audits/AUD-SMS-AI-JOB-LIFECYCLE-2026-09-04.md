# AUD-SMS-AI-JOB-LIFECYCLE-2026-09-04

- **Prompt ID:** AUD-SMS-JOB-LIFECYCLE-r2
- **Phase:** AUD-SMS-JOB — SMS/AI + Job Lifecycle Deep Audit
- **Context Pack:** CP-AUD-SMS-JOB-r2
- **Review:** PRV-AUD-SMS-JOB-r2 — PASS
- **Base Commit:** `acf2cf1f88bc9f6db8ca52c4e4619b16634890f7`
- **Role:** CallUpp Data Lifecycle / Privacy Auditor
- **Target File:** `docs/audits/AUD-SMS-AI-JOB-LIFECYCLE-2026-09-04.md`

---

## Executive Summary

This audit performs a deep, static inspection of the CallUpp Android codebase across two coupled subsystems:
1. **Audit A: SMS/AI Privacy & Analysis-Window Safety** (SP-025 through SP-040, SP-047, SP-048, SP-056, SP-057, SP-059, SP-064)
2. **Audit B: Job Status, Reopen, New Job & +24h Lifecycle** (SP-017 through SP-021, SP-047, SP-056, SP-057, SP-065, SP-066)

All conclusions are classified strictly into:
- `[DOCUMENTED IMPLEMENTATION]`: Direct, verified code/manifest/schema evidence.
- `[INFERENCE]`: Logical consequence derived from verified code paths.
- `[RUNTIME UNKNOWN]`: Platform-, OS-, or timing-dependent behavior requiring device execution.

---

## AUDIT A — SMS / AI Privacy & Analysis-Window Safety

### 1. Whether raw SMS body is ever persisted in Room
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/entity/SmsTriggerEntity.kt:17-25`, `app/src/main/java/com/example/data/database/CallUppDatabase.kt:16-30`
- **Findings:**
  - `SmsTriggerEntity` defines columns: `id`, `clientId`, `senderPhoneKey`, `receivedAt`, `state`, `attemptCount`, `createdAt`. No message body column exists.
  - Across all 11 Room entities in `CallUppDatabase`, only `NoteEntity.rawText` (phone/manual notes), `AiSuggestionEntity.proposedValueJson` (structured candidates), `JobEntity.smsSummary` (condensed summary), and `SmsTemplateEntity.body` (outbound templates) store textual data.
  - Raw incoming SMS text is never saved to the local database.

### 2. Exact SmsTrigger metadata stored
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/entity/SmsTriggerEntity.kt:17-25`, `app/src/main/java/com/example/core/model/Enums.kt:53-58`
- **Findings:**
  - `id: String` (UUID Primary Key)
  - `clientId: String` (Foreign client identifier, indexed)
  - `senderPhoneKey: String` (Normalized phone key, indexed)
  - `receivedAt: Long` (Epoch millis from SMS PDU)
  - `state: TriggerState` (`PENDING`, `PROCESSED`, `DISCARDED`, `FAILED`, indexed)
  - `attemptCount: Int` (Default 0)
  - `createdAt: Long` (Timestamp of trigger creation)
  - Exactly reflects canonical specification in SP-047 §47.

### 3. Whether SMS_RECEIVED acts only as trigger
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:25-30,67`
- **Findings:**
  - In `SmsReceiver.onReceive`, the receiver extracts the full SMS message body directly from intent extras at line 30:
    `val fullBody = msgs.joinToString("") { it.messageBody ?: "" }`
  - This extraction occurs before checking global preferences, client existence, or active job analysis windows.
  - At line 67, `fullBody` is passed directly in memory to `smsAnalysisCoordinator.processSmsTrigger(triggerId, fullBody)`.
  - The broadcast does not act solely as an opaque trigger; it extracts the payload in the receiver component and pipes it into an in-memory coroutine.

### 4. Whether a worker exists for deferred analysis
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:35-68`, directory `app/src/main/java/com/example/system/work/`
- **Findings:**
  - Directory `app/src/main/java/com/example/system/work/` contains only `JobStatusReconciler.kt` and `TrashCleanupWorker.kt`.
  - There is no `Worker` or `CoroutineWorker` for SMS extraction or deferred trigger processing.
  - Analysis is launched directly inside `app.container.appScope.launch` in `SmsReceiver`.
  - If the application process terminates before extraction completes, the trigger remains `PENDING` in Room and is never picked up or resumed.

### 5. Whether a specific system SMS is re-read by worker vs body passed/persisted
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:30,67`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:43`
- **Findings:**
  - The message body is captured in memory from `Telephony.Sms.Intents.getMessagesFromIntent(intent)` inside `SmsReceiver` and passed as a string parameter `smsBody` into `processSmsTrigger`.
  - There is no query to Android's system SMS ContentProvider (`content://sms` or `Telephony.Sms`) by any worker or repository.

### 6. Global OFF behavior
- **Status:** PARTIAL / PRIVACY DEFECT
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:25-38`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:47-51`, `app/src/main/java/com/example/data/preferences/AppPreferences.kt:25-27`
- **Findings:**
  - `SmsReceiver.kt:25-30`: `SmsReceiver.onReceive` extracts and joins the full SMS body (`val fullBody = msgs.joinToString("") { it.messageBody ?: "" }`) unconditionally upon broadcast arrival, BEFORE inspecting `smsAnalysisGlobalEnabled`.
  - At line 37-38: `val globalEnabled = app.container.appPreferences.smsAnalysisGlobalEnabled.first(); if (!globalEnabled) return@launch`. While AI extraction and Room trigger insertion are skipped when global analysis is disabled, the SMS content has already been parsed and read in memory.
  - In `SmsAnalysisCoordinator.kt:47-51`: A redundant gate validates `globalEnabled`; if false, it marks trigger `DISCARDED` and aborts.
  - Assessment: SP-027 specifies that when global analysis is OFF, SMS content must not be analyzed or read by the feature. While raw text is not persisted in Room and not sent to AI, the receiver reads the SMS body from the intent before checking this setting.

### 7. Client OFF behavior
- **Status:** PARTIAL / PRIVACY DEFECT
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:25-42`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:59-62`, `app/src/main/java/com/example/core/model/Enums.kt:18-22`
- **Findings:**
  - `SmsReceiver.kt:25-30`: Similar to Global OFF, the full SMS message body is read into memory before checking the client record or verifying `client.smsAnalysisMode`.
  - At line 41-42: If `client.smsAnalysisMode == SmsAnalysisMode.DISABLED`, the coroutine returns without triggering AI or persisting a trigger record.
  - In `SmsAnalysisCoordinator.kt:59-62`: Re-evaluates client setting; if `DISABLED`, marks trigger `DISCARDED` and aborts.
  - Assessment: The gate successfully prevents AI processing, trigger creation, and Room persistence, but violates the privacy expectation that disabled clients do not have their incoming SMS content parsed/read into memory.

### 8. No-ACTIVE-job behavior
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:45-53`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:65-69`
- **Findings:**
  - `SmsReceiver.kt:45-53`: If `app.container.jobRepository.getActiveJobsForClientSync(client.id)` is empty, the receiver invokes `reengagementRepository.checkAndCreateReengagementEvent(clientId, INCOMING_SMS)` and exits immediately (`return@launch`).
  - No `SmsTriggerEntity` is inserted and no AI processing occurs.
  - `SmsAnalysisCoordinator.kt:65-69` also ensures that if active jobs are empty, trigger is marked `DISCARDED`.

### 9. Multiple ACTIVE jobs
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:71-78,115,153-167,198-230,251-264`
- **Findings:**
  - Finds all client active jobs with valid open analysis windows covering the SMS arrival timestamp (`eligibleJobs`).
  - All eligible active job IDs, terms, and summaries are supplied to `SmsExtractionInput`.
  - Address candidate: Propagates to all eligible active jobs that have empty address snapshots.
  - Term candidate: For each eligible job, if preliminary/confirmed term is empty, fills preliminary term; if term already exists, generates an `AiSuggestionEntity` targeted specifically to that `job.id`.
  - Summaries: Updates `smsSummary` for each active job included in the extraction result.

### 10. JobAnalysisWindow eligibility
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:73-78`, `app/src/main/java/com/example/data/dao/JobAnalysisWindowDao.kt:13`
- **Findings:**
  - Evaluates: `val window = windowDao.getOpenWindowForJob(job.id)` followed by `if (window != null && trigger.receivedAt >= window.startedAt) eligibleJobs.add(job)`.
  - Requires `endedAt IS NULL` and `receivedAt >= window.startedAt`.

### 11. SMS before job
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:75,80-83`, `app/src/main/java/com/example/data/repository/JobRepository.kt:56`
- **Findings:**
  - New jobs initialize `JobAnalysisWindowEntity.startedAt` to current timestamp `System.currentTimeMillis()`.
  - If `trigger.receivedAt < window.startedAt`, the job is excluded from `eligibleJobs`.
  - If no eligible jobs remain, the trigger state is marked `DISCARDED`.

### 12. SMS between closed/reopened windows
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:74-78`, `app/src/main/java/com/example/data/repository/JobRepository.kt:78,91,104-109`, `app/src/main/java/com/example/data/dao/JobAnalysisWindowDao.kt:13`
- **Findings:**
  - On `completeJob` or `closeJob`, all existing windows have `endedAt` set to closing time.
  - On `reopenJob`, a new window is inserted with `startedAt = now`.
  - An SMS received in the closed interval has `trigger.receivedAt < newWindow.startedAt`, which fails eligibility on the reopened window. Closed windows are ignored (`endedAt IS NOT NULL`).

### 13. SMS after completion
- **Status:** PARTIAL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]` & `[INFERENCE]`
- **Locator:** `app/src/main/java/com/example/system/sms/SmsReceiver.kt:45-53`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:71-78,153-167,198-209,254-263`
- **Findings:**
  - Initial check: If all jobs are completed/closed, `SmsReceiver` routes to reengagement and aborts before AI.
  - Completed jobs have closed analysis windows (`endedAt != null`) and status `COMPLETED`, excluding them from `activeJobs` and `eligibleJobs`.
  - In-flight race vulnerability: If a job transitions to `COMPLETED` while `extractionEngine.extract(input)` is executing, `withTransaction` re-checks `job.status == JobStatus.ACTIVE` only for `smsSummary` (line 255). It does NOT re-check status for address propagation (line 153) or term updates (line 198), potentially mutating a completed job entity.

### 14. Retry eligibility and stale retry protection
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:123-125`, `app/src/main/java/com/example/data/dao/SmsTriggerDao.kt:20-27`
- **Findings:**
  - Failed extraction transitions trigger state to `TriggerState.FAILED` (`SmsAnalysisCoordinator.kt:124`).
  - However, no retry scheduler, WorkManager worker, or queue processor exists anywhere in the codebase.
  - Because retries are never initiated, stale retry invalidation logic (SP-038 §38) does not exist.

### 15. Pending work after COMPLETED/CLOSED
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:69-93`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:153-167,198-209`
- **Findings:**
  - Neither `completeJob` nor `closeJob` cancels running coroutines or invalidates `PENDING` trigger records in Room.
  - In-flight analysis coroutines continue execution and will commit address and term updates to jobs in `withTransaction` without checking whether the job was closed during processing.

### 16. AI input minimization
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/model/AiCandidateModels.kt:17-26`, `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:86-118`
- **Findings:**
  - `SmsExtractionInput` bundles only: `smsBody`, `receivedTimestamp`, `localDateTime`, `timezone`, `clientAddress`, `activeJobIds`, `activeJobTerms`, `activeJobSummaries`.
  - Does not include contact books, call logs, customer notes, or details of other clients/jobs. Matches SP-030 and SP-059.

### 17. Notes/history leakage
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:85-118`, `app/src/main/java/com/example/ai/model/AiCandidateModels.kt:17-26`
- **Findings:**
  - `JobEntity.manualNotes`, `NoteEntity.rawText`, and call log histories are completely omitted from the AI input model. Zero leakage detected.

### 18. Unknown jobId protection
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:251-255`
- **Findings:**
  - Summary updates from the model are checked against `eligibleJobIdSet` and validated via `jobDao.getJobByIdSync(summaryUpdate.jobId)`.
  - Unknown or mismatched IDs returned by AI are ignored.

### 19. Address protection
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:136-193`, `app/src/main/java/com/example/ai/model/AiCandidateModels.kt:37-39`
- **Findings:**
  - If client has an existing address (`!clientAddress.isEmpty`), AI never overwrites it; if a different address is extracted, it creates an `AiSuggestionEntity(type = ADDRESS_CHANGE)` with status `PENDING`.
  - If client address is empty, AI auto-fills only when `confidence == "HIGH"` and `addr.isCompleteEnough` is true.

### 20. Term protection
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:196-230`
- **Findings:**
  - If a job already has a preliminary term or confirmed start date, AI never overwrites it; it generates an `AiSuggestionEntity(type = TERM_CHANGE)` with status `PENDING`.
  - If term is blank, AI populates preliminary date/time fields only with `confidence == "HIGH"`. It never populates `confirmedStartAt` or auto-creates Calendar events.

### 21. Invalid JSON/fail-closed path
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:121-131,137,197`
- **Findings:**
  - Parsing exceptions or extraction engine errors are caught in `try/catch`; trigger is marked `FAILED` and method returns false without database changes.
  - Low confidence (`confidence != "HIGH"`) candidates are discarded.

### 22. Summary freeze
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/ai/SmsAnalysisCoordinator.kt:254-263`, `app/src/main/java/com/example/data/repository/JobRepository.kt:78,91`
- **Findings:**
  - `completeJob` and `closeJob` close the analysis windows and transition status out of `ACTIVE`.
  - `SmsAnalysisCoordinator` only applies summary updates if `job.status == JobStatus.ACTIVE` and window is open.

### 23. Firebase implementation state vs fake engine
- **Status:** PASS (Consistent with v1 specification phase)
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/core/di/AppContainer.kt:26,88`, `app/src/main/java/com/example/ai/FakeSmsExtractionEngine.kt:16-108`, `app/src/main/java/com/example/ai/SmsExtractionEngine.kt:9-13`
- **Findings:**
  - `FakeSmsExtractionEngine` is currently wired in `AppContainer`.
  - `FirebaseSmsExtractionEngine` does not exist yet. This conforms to SP-064 §64, which specifies using `FakeSmsExtractionEngine` during initial phases before introducing Firebase in Android Studio.

---

## AUDIT B — Job Lifecycle

### 1. Status enum implementation
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/core/model/Enums.kt:1-5`, `app/src/main/java/com/example/data/entity/JobEntity.kt:53`
- **Findings:**
  - Defined as `enum class JobStatus { ACTIVE, COMPLETED, CLOSED }`. Exactly matches SP-017 §17.

### 2. Archive vs deletedAt separation
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/entity/JobEntity.kt:54-55`, `app/src/main/java/com/example/data/dao/JobDao.kt:15-22,68-85`, `app/src/main/java/com/example/data/repository/JobRepository.kt:113-143`
- **Findings:**
  - Separate columns: `isArchived: Boolean = false` and `deletedAt: Long? = null`.
  - Active/archived/deleted jobs are queried and managed through independent DAO queries and repository functions.

### 3. JobAnalysisWindow creation/close/reopen
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:47-63,69-80,82-93,95-111`, `app/src/main/java/com/example/data/repository/CallDraftRepository.kt:359-364`
- **Findings:**
  - Created in `JobRepository.createJob` and `CallDraftRepository.commitOverlaySession` (`reason = WindowReason.CREATED`).
  - Closed in `completeJob`, `closeJob`, and `softDeleteJob` via `windowDao.closeAllWindowsForJob(jobId, now)`.
  - Reopened in `reopenJob` and `restoreJob` (`reason = WindowReason.REOPENED`).

### 4. Exactly one open window invariant
- **Status:** PARTIAL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]` & `[INFERENCE]`
- **Locator:** `app/src/main/java/com/example/data/entity/JobAnalysisWindowEntity.kt:10-24`, `app/src/main/java/com/example/data/repository/JobRepository.kt:95-111,125-139`, `app/src/main/java/com/example/data/dao/JobAnalysisWindowDao.kt:13-14`
- **Findings:**
  - Preserved in normal workflows through sequential calls.
  - However, no database unique constraint exists on `(jobId, endedAt)` to prevent duplicate open windows.
  - `reopenJob` and `restoreJob` insert a new window without first ensuring existing open windows are closed, and `getOpenWindowForJob` masks potential duplicates with `LIMIT 1`.

### 5. ACTIVE→COMPLETED path
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:69-80`, `app/src/main/java/com/example/system/work/JobStatusReconciler.kt:28-30`, `app/src/main/java/com/example/ui/screens/JobDetailScreen.kt:325-328`
- **Findings:**
  - Triggered manually from UI ("Oznacz jako wykonane") or automatically via `JobStatusReconciler`.
  - Inside Room transaction: updates status to `COMPLETED`, records `completedAt`, and closes all analysis windows with `endedAt = now`.

### 6. ACTIVE→CLOSED path
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:82-93`, `app/src/main/java/com/example/ui/screens/JobDetailScreen.kt:338-348,371-379`
- **Findings:**
  - Triggered manually from UI ("Zamknij zlecenie").
  - Inside Room transaction: updates status to `CLOSED`, records `closedAt`, and closes all analysis windows with `endedAt = now`.

### 7. +24h anchor calculation
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:169-182`
- **Findings:**
  - `calculateCompletionAnchor` implements exact hierarchy:
    1. `confirmedStartAt` (if present)
    2. `preliminaryDateEpochDay + preliminaryTimeMinute`
    3. `preliminaryDateEpochDay` date-only -> 23:59:59 end-of-day
    4. No date -> `null`

### 8. confirmedStartAt precedence
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:170-172`
- **Findings:**
  - Evaluated first: `if (job.confirmedStartAt != null) return job.confirmedStartAt`.

### 9. Preliminary date+time
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:176-178`
- **Findings:**
  - Calculates `LocalDateTime.of(date, LocalTime.of(timeMinute / 60, timeMinute % 60))`.

### 10. Date-only end-of-day behavior
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:178-180`
- **Findings:**
  - Computes `LocalDateTime.of(date, LocalTime.of(23, 59, 59))`.

### 11. No-date behavior
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:173`, `app/src/main/java/com/example/system/work/JobStatusReconciler.kt:28`
- **Findings:**
  - Returns `null` when no date exists; `JobStatusReconciler` skips completion when anchor is null.

### 12. Per-job WorkManager scheduling existence or absence
- **Status:** FAIL (ABSENT)
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/CallUppApplication.kt:41-65`, `app/src/main/java/com/example/data/repository/JobRepository.kt:47-68`
- **Findings:**
  - There is no per-job `AutoCompleteWorker` or `OneTimeWorkRequest` scheduled for `anchor + 24h`.
  - Only periodic 6-hour reconciler exists in `CallUppApplication`.

### 13. Cancel/reschedule on term change
- **Status:** FAIL (ABSENT)
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:65-67`, `app/src/main/java/com/example/data/repository/AiSuggestionRepository.kt:86-107`
- **Findings:**
  - Because per-job workers do not exist, no cancellation or rescheduling occurs when terms are updated or accepted.

### 14. JobStatusReconciler behavior
- **Status:** PARTIAL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/system/work/JobStatusReconciler.kt:14-37`, `app/src/main/java/com/example/CallUppApplication.kt:54-61`
- **Findings:**
  - Reconciles active jobs where `(now - anchor) >= 24h` and marks them `COMPLETED`.
  - However, it is scheduled only as a periodic 6-hour background worker; it is NOT invoked on application startup in `CallUppApplication.onCreate()`, violating SP-018 §18.

### 15. Reopen old past term protection
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]` & `[INFERENCE]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:95-111,169-182`, `app/src/main/java/com/example/system/work/JobStatusReconciler.kt:25-31`
- **Findings:**
  - SP-019 §19 requires: if a reopened job has an old past term, do not schedule auto-completion on it.
  - In code, `reopenJob` retains the previous term in `JobEntity`.
  - `calculateCompletionAnchor` and `JobStatusReconciler` do not compare `anchor` against `job.reopenedAt`.
  - Because the past anchor is older than 24 hours, `JobStatusReconciler` immediately re-completes the newly reopened job on its next run.

### 16. New job existing client copy behavior
- **Status:** PARTIAL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/ReengagementRepository.kt:51-72`, `app/src/main/java/com/example/ui/screens/NewJobScreen.kt:114-118,183-204`
- **Findings:**
  - `ReengagementRepository.createNewJobFromPrevious` copies service and price, but copies address from `previousJob.addressCitySnapshot` instead of reading `ClientEntity.city`.
  - `NewJobScreen.kt` pre-fills client address, but does not auto-copy the previous service or price from history.

### 17. Term empty on new job
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/entity/JobEntity.kt:37-41`, `app/src/main/java/com/example/data/repository/ReengagementRepository.kt:53-68`, `app/src/main/java/com/example/ui/screens/NewJobScreen.kt:190-199`
- **Findings:**
  - Preliminary and confirmed term fields default to `null` and are not populated from previous jobs.

### 18. SMS summary empty on new job
- **Status:** PASS
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/entity/JobEntity.kt:51`, `app/src/main/java/com/example/data/repository/ReengagementRepository.kt:53-68`, `app/src/main/java/com/example/ui/screens/NewJobScreen.kt:190-199`
- **Findings:**
  - `smsSummary` defaults to `null` and is never carried over to new jobs.

### 19. Duplicate active-term warning logic
- **Status:** FAIL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/JobRepository.kt:147-160`
- **Findings:**
  - `checkHasDuplicateActiveTerm` is implemented in `JobRepository`, but is never invoked from any UI screen, ViewModel, or repository method.
  - The warning "Klient ma inne aktywne zlecenie w tym samym terminie" is never displayed.

### 20. Reengagement behavior
- **Status:** PARTIAL
- **Classification:** `[DOCUMENTED IMPLEMENTATION]`
- **Locator:** `app/src/main/java/com/example/data/repository/ReengagementRepository.kt:21-44`, `app/src/main/java/com/example/ui/screens/CallsScreen.kt:74,256-281`
- **Findings:**
  - Reengagement events are created for incoming calls/SMS from clients with past jobs and no active jobs (max 1 pending event per client).
  - However, `ReengagementDialog` is implemented only in `CallsScreen.kt`; it is absent from `JobsScreen.kt` and `ClientDetailScreen.kt`, contrary to SP-039 §39.

---

## PRIVACY-CRITICAL FAILURES

1. **Early SMS Body Extraction in BroadcastReceiver (`SmsReceiver.kt:30`)**
   - The full SMS body string is assembled from intent PDUs immediately upon receiving the broadcast, before verifying client registration, global preferences (`smsAnalysisGlobalEnabled`), or active job analysis windows. This compromises Global OFF and Client OFF privacy boundaries by reading/parsing message content into memory regardless of user/client configuration.
2. **In-Memory Pipe Instead of Worker Provider Re-Read (`SmsReceiver.kt:67`)**
   - The receiver directly forwards the extracted SMS body string to an in-memory coroutine, bypassing the architectural boundary requiring a worker to selectively re-read the message from the system provider.
3. **In-Flight Job Completion Data Leakage (`SmsAnalysisCoordinator.kt:153-167, 198-209`)**
   - When applying address candidates and preliminary terms inside `withTransaction`, the coordinator modifies `eligibleJobs` without re-checking `job.status == JobStatus.ACTIVE`. If a job is completed while extraction is running, data will be written to completed jobs.

---

## LIFECYCLE-CRITICAL FAILURES

1. **Reopened Jobs with Past Terms Are Immediately Re-Completed (`JobStatusReconciler.kt:28`, `JobRepository.kt:169-182`)**
   - Reopened jobs retain their prior terms. Neither `calculateCompletionAnchor` nor `JobStatusReconciler` accounts for `reopenedAt`. Because the past term is older than 24h, the reconciler automatically re-completes the reopened job on its next run, violating SP-019.
2. **Complete Absence of Per-Job WorkManager Scheduling (`CallUppApplication.kt:41-65`)**
   - No `AutoCompleteWorker` or `OneTimeWorkRequest` is scheduled for `anchor + 24h` on job creation or term changes. Auto-completion depends solely on a coarse 6-hour periodic poll.
3. **Missing Term Rescheduling / Worker Cancellation (`JobRepository.kt:65-67`)**
   - Modifying or accepting a term does not cancel or reschedule background auto-completion workers because per-job workers do not exist.
4. **Dead Code: Duplicate Active-Term Warning (`JobRepository.kt:147-160`)**
   - `checkHasDuplicateActiveTerm` is never called. Soft validation warnings for overlapping terms (SP-021 §21) are absent from all UI flows.

---

## PARTIAL IMPLEMENTATIONS

1. **JobStatusReconciler Startup Execution (`CallUppApplication.kt:54-61`)**
   - The reconciler logic is implemented and scheduled periodically every 6 hours, but is not run during application startup in `CallUppApplication.onCreate()`.
2. **Reengagement UI Placement (`CallsScreen.kt:256-281`)**
   - `ReengagementDialog` is implemented on `CallsScreen`, but missing on `JobsScreen` and `ClientDetailScreen`.
3. **New Job Data Propagation (`ReengagementRepository.kt:59`)**
   - `createNewJobFromPrevious` copies the address snapshot from the previous job rather than the primary `ClientEntity` record.
4. **Trigger Failure Recording Without Retry Queue (`SmsAnalysisCoordinator.kt:124`)**
   - Trigger states transition to `FAILED`, but no retry worker or queue processor exists.

---

## CURRENTLY SAFE BEHAVIORS

1. **Zero Raw SMS Persistence:** `SmsTriggerEntity` and Room entities store only metadata; SMS text is never stored in SQLite/Room.
2. **AI & Storage Halting on Disabled Settings:** When `smsAnalysisGlobalEnabled == false` or `client.smsAnalysisMode == DISABLED`, AI extraction is never invoked and no trigger record is persisted in Room (though pre-reading occurs in the receiver).
3. **No-Active-Job Gate:** SMS from clients without active jobs routes exclusively to reengagement event detection without AI processing.
4. **Analysis Window Bounding:** SMS received before job start or between closed/reopen windows is excluded from analysis.
5. **Non-Destructive AI Safeguards:** AI never overwrites existing addresses or terms; it creates non-destructive `AiSuggestionEntity` records.
6. **Input Minimization:** Extraneous entities (notes, call logs, contacts) are completely excluded from AI payloads.
7. **Fail-Closed Processing:** Extraction errors or low confidence results fail safely without mutating data.
8. **Summary Freeze:** Completed and closed jobs have closed windows and reject summary modifications.
9. **Status Model Separation:** `JobStatus`, `isArchived`, and `deletedAt` operate independently.

---

## RUNTIME UNKNOWNS

1. **Process Death During In-Memory Extraction:** Because extraction is dispatched via `appScope.launch` from `SmsReceiver` rather than an isolated persistent WorkManager worker, process termination by Android OS will drop pending triggers.
2. **Periodic Reconciler Delay Under Android Doze:** Standard periodic WorkManager tasks (6-hour interval) are deferred under Doze mode and manufacturer power management, meaning jobs may remain `ACTIVE` well past `anchor + 24h` unless startup reconciliation runs.

---

## RESEARCH-DEPENDENT UNKNOWNS

1. **Modern Android Telephony Provider Access for Non-Default SMS Apps:** On Android 14+ (API 34-36), reading `Telephony.Sms` content provider records requires `READ_SMS` runtime permission and may be subject to Google Play privacy restrictions or system limitations for non-default SMS apps.
2. **Firebase AI Engine Seam & Latency:** Replacing `FakeSmsExtractionEngine` with `FirebaseSmsExtractionEngine` will introduce remote network latency, rate limits, and JSON token variations that require live backend validation.
