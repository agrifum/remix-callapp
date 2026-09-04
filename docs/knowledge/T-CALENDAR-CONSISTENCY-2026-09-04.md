# T-CALENDAR-CONSISTENCY-2026-09-04

## Question
For Android API 31–36 / targetSdk 36, what consistency guarantees and failure modes apply when implementing CallUpp's required lifecycle:
- manual confirmation → Calendar Provider event creation → persist `calendarEventId`
- term update updates owned event
- Job soft-delete deletes owned event
- AI accepted term may update Calendar and reschedule job completion
while Room, Calendar Provider / ContentResolver, and WorkManager are separate systems? Research what is DOCUMENTED and what must remain an INFERENCE.

## Applicable product SP IDs
- **SP-016**: EKRAN — Pełne zlecenie (sekcja terminu, potwierdzenie kalendarza `Potwierdź termin i dodaj do kalendarza`, sekcja AI propozycji terminu)
- **SP-034**: AI — termin (brak terminu vs termin istnieje; aktualizacja Calendar event po akceptacji propozycji)
- **SP-046**: Calendar (Android Calendar Provider; brak OAuth/Google Calendar API; utworzenie tylko po ręcznym potwierdzeniu; zmiana terminu aktualizuje event; miękkie usunięcie Job usuwa należący event; stały czas trwania v1 = 60 minut)
- **SP-051**: Uprawnienia — wymagane (`READ_CALENDAR`, `WRITE_CALENDAR`)
- **SP-056**: Transakcje krytyczne (sekcja `Accept AI term`: Job term, Calendar update jeśli dotyczy, reschedule AutoCompleteWorker, AiSuggestion accepted)
- **SP-057**: Główne przepływy (FLOW G: AI wykrywa pusty termin; FLOW H: AI wykrywa nowy termin, akceptacja aktualizuje Calendar i reschedule +24h worker; FLOW I: AutoCompleteWorker)
- **SP-058**: Stabilność (odmowa Calendar permission oraz zabicie procesu aplikacji nie mogą uniemożliwić zapisu notatki, utworzenia klienta, utworzenia zlecenia ani korzystania z historii)
- **SP-066**: Definition of Done v1 (pkt 14: Calendar działa w create/update/delete; pkt 19: żaden brak opcjonalnego permission nie powoduje crasha)
- **SP-068**: Zasada dalszego developmentu (Calendar jest adapterem wokół rdzenia ROZMOWA → NOTATKA → KLIENT → ZLECENIE; jego awaria nie może zaburzać podstawowego przepływu)

## Checked-at date
2026-09-04

## Applicable Android/API versions
Android API 31–36 (`minSdk = 31`, `compileSdk = 36`, `targetSdk = 36`, Java 17 / Kotlin Coroutines / AndroidX)

---

## Technical Findings Table

| claim ID | DOCUMENTED FACT / INFERENCE / UNKNOWN | finding | official URL/title/publisher | applicable version | confidence | limitation | consequence for later implementation prompt |
|---|---|---|---|---|---|---|---|
| **T-CAL-01** | DOCUMENTED FACT | Non-recurring event insertion into `CalendarContract.Events.CONTENT_URI` requires `CALENDAR_ID` (`Long`), `DTSTART` (UTC ms), `DTEND` (UTC ms), and `EVENT_TIMEZONE` (`String`). Omitting any mandatory field causes CalendarProvider to throw `IllegalArgumentException` or fail insertion. | [Calendar Provider Overview](https://developer.android.com/guide/topics/providers/calendar-provider) / [CalendarContract.Events](https://developer.android.com/reference/android/provider/CalendarContract.Events) (Google / Android Developers) | API 14+ (verified API 31–36) | HIGH | Platform documentation explicitly states these four fields are strictly mandatory for all non-recurring inserts. | Implementation prompt must ensure `ContentValues` always includes valid `CALENDAR_ID`, `DTSTART`, `DTEND` (`DTSTART + 3600000L`), and `EVENT_TIMEZONE` (e.g. `ZoneId.systemDefault().id`). |
| **T-CAL-02** | DOCUMENTED FACT | `ContentResolver.insert(CalendarContract.Events.CONTENT_URI, values)` returns a row `Uri?` on success formatted as `content://com.android.calendar/events/<id>`. The numeric event ID is extracted using `ContentUris.parseId(uri)` returning a `Long`. If insertion fails, `insert` returns `null`. | [ContentResolver.insert](https://developer.android.com/reference/android/content/ContentResolver#insert(android.net.Uri,%20android.content.ContentValues)) / [ContentUris.parseId](https://developer.android.com/reference/android/content/ContentUris#parseId(android.net.Uri)) (Google / Android Developers) | API 1+ (verified API 31–36) | HIGH | `ContentUris.parseId` throws `UnsupportedOperationException` or `NumberFormatException` if URI is not hierarchical or ID segment is non-numeric. | Implementation prompt must verify `uri != null` before calling `ContentUris.parseId(uri)` and store the parsed `Long` directly into `JobEntity.calendarEventId`. |
| **T-CAL-03** | DOCUMENTED FACT | For non-recurring events, `DTEND` must be set and `DURATION` must remain null. For recurring events, `DURATION` (RFC 5545) and `RRULE` or `RDATE` are required instead of `DTEND`. Setting both `DTEND` and `DURATION` is prohibited by provider validation rules. | [CalendarContract.Events](https://developer.android.com/reference/android/provider/CalendarContract.Events) (Google / Android Developers) | API 14+ (verified API 31–36) | HIGH | Fixed duration in CallUpp v1 (SP-046) is 60 minutes. Event calculation is `DTEND = DTSTART + 60 * 60 * 1000L`. | Implementation prompt must never provide `DURATION` when inserting or updating non-recurring 60-minute CallUpp job events. |
| **T-CAL-04** | DOCUMENTED FACT | Updating an event via `ContentResolver.update(uri, values, null, null)` using `ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)` returns an `Int` representing affected row count. If the event was deleted externally (e.g. via Google Calendar app), `update` returns `0` and does NOT throw an exception. | [ContentResolver.update](https://developer.android.com/reference/android/content/ContentResolver#update(android.net.Uri,%20android.content.ContentValues,%20java.lang.String,%20java.lang.String[])) (Google / Android Developers) | API 1+ (verified API 31–36) | HIGH | Return value of `0` indicates no matching row exists or caller lacks write permission on that specific calendar. | Implementation prompt must check the returned row count: if `0`, handle the missing event gracefully (e.g., clear `calendarEventId` or prompt user to recreate). |
| **T-CAL-05** | DOCUMENTED FACT | Deleting an event via `ContentResolver.delete(uri, null, null)` using `ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)` returns an `Int` representing deleted row count. If the event does not exist, it returns `0` without throwing an exception. | [ContentResolver.delete](https://developer.android.com/reference/android/content/ContentResolver#delete(android.net.Uri,%20java.lang.String,%20java.lang.String[])) (Google / Android Developers) | API 1+ (verified API 31–36) | HIGH | Deleting an already non-existent event is inherently idempotent regarding desired end state (0 rows deleted means 0 events remain). | Implementation prompt can treat `delete` returning `0` or `1` as success during Job soft-delete, clearing `calendarEventId` in Room. |
| **T-CAL-06** | DOCUMENTED FACT | Accessing `CalendarContract` requires dangerous runtime permissions `android.permission.READ_CALENDAR` (to query calendars/events) and `android.permission.WRITE_CALENDAR` (to insert/update/delete events). Invoking `ContentResolver` operations without granted permissions throws `java.lang.SecurityException`. | [CalendarContract](https://developer.android.com/reference/android/provider/CalendarContract) / [Permissions Overview](https://developer.android.com/guide/topics/permissions/overview) (Google / Android Developers) | API 23+ (verified API 31–36) | HIGH | Permissions can be granted or revoked at any time in system Settings while app process is alive or dead. | Implementation prompt must guard all Calendar operations with permission checks (`ContextCompat.checkSelfPermission`) and catch `SecurityException` to prevent crashes (SP-058, SP-066 #19). |
| **T-CAL-07** | DOCUMENTED FACT | To obtain a valid `CALENDAR_ID`, an app queries `CalendarContract.Calendars.CONTENT_URI`. A writable calendar requires `CALENDAR_ACCESS_LEVEL >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR` (value 500). Primary account calendar is identified by `IS_PRIMARY == "1"`. Visible calendars have `VISIBLE == 1`. | [CalendarContract.Calendars](https://developer.android.com/reference/android/provider/CalendarContract.Calendars) (Google / Android Developers) | API 14+ (verified API 31–36) | HIGH | On a freshly initialized device or emulator without configured accounts or local calendars, the query may return 0 rows. Attempting insert with a non-existent `CALENDAR_ID` throws `IllegalArgumentException` or fails. | Implementation prompt must resolve calendar ID dynamically: query writable calendars filtered by `CALENDAR_ACCESS_LEVEL >= 500`, prefer `IS_PRIMARY == 1` and `VISIBLE == 1`. If 0 calendars exist, disable calendar sync gracefully without crashing. |
| **T-CAL-08** | DOCUMENTED FACT | Calendar events belong to an account (`ACCOUNT_NAME`, `ACCOUNT_TYPE`). If the calendar is synced with a cloud provider (e.g. Google Calendar Sync Adapter), the event is synchronized upstream. If the user removes the account from Android Settings or wipes calendar data, all associated events in CalendarProvider are cascade-deleted by the system. | [Calendar Provider: Calendars Table](https://developer.android.com/guide/topics/providers/calendar-provider#calendar-table) (Google / Android Developers) | API 14+ (verified API 31–36) | HIGH | CalendarProvider does not send targeted push notifications to non-sync apps when an individual event is modified or deleted externally, unless a `ContentObserver` is registered. | Implementation prompt must anticipate that a stored `calendarEventId` in Room may become invalid/orphaned over time due to external calendar actions. |
| **T-CAL-09** | DOCUMENTED FACT | Room transactions (`@Transaction`, `RoomDatabase.runInTransaction`, `roomDatabase.withTransaction`) operate strictly on the local application SQLite database connection (`android.database.sqlite.SQLiteDatabase` / SQLite `BEGIN...COMMIT/ROLLBACK`). External `ContentProvider` operations communicate via Android Binder IPC to an external system process (`com.android.providers.calendar`) and CANNOT participate in a Room SQLite transaction. | [RoomDatabase.withTransaction](https://developer.android.com/reference/androidx/room/RoomDatabase#withTransaction(kotlin.Function1)) / [SQLiteDatabase Transactions](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#beginTransaction()) (Google / Android Developers) | Room 2.x/3.x, API 31–36 | HIGH | IPC calls made inside a Room transaction block execute and commit immediately in the remote CalendarProvider database. If the Room transaction rolls back afterwards, the remote CalendarProvider change is NOT rolled back automatically. | Implementation prompt must acknowledge that SP-056's literal phrasing ("Następujące akcje wykonywać w jednej Room transaction: Accept AI term: Job term, Calendar update...") cannot provide hardware/database atomicity for external IPC operations. |
| **T-CAL-10** | DOCUMENTED FACT | Android provides NO cross-provider distributed transaction coordinator, two-phase commit (2PC / XA), or atomic transaction API bridging Room SQLite, ContentResolver, and WorkManager. Each subsystem maintains independent persistence boundaries and independent transactions. | [Android Architecture Guide: Data Layer](https://developer.android.com/topic/architecture/data-layer) / [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver) (Google / Android Developers) | All Android versions (API 1–36) | HIGH | No platform mechanism exists to roll back ContentResolver IPC or WorkManager scheduling if local SQLite fails, or vice versa. | Cross-system consistency across Room, CalendarProvider, and WorkManager must be handled at the application layer using compensation or outbox/reconciliation logic. |
| **T-CAL-11** | DOCUMENTED FACT | The Android OS may terminate an application process at any time (e.g. system memory reclamation via `LowMemoryKiller`, process death, or crash). If process termination occurs between independent persistence calls across different subsystems, state changes executed prior to termination persist while subsequent calls never execute. | [Processes and Application Lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle) (Google / Android Developers) | API 1+ (verified API 31–36) | HIGH | Process death can occur at arbitrary points between statements. | Consequence for later implementation prompt: cross-system sequences (Room write, CalendarProvider IPC, WorkManager enqueue) must account for partial execution across crashes without corrupting core state. |
| **T-CAL-12** | DOCUMENTED FACT | `WorkManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequest)` cancels any existing incomplete work with the specified unique name and enqueues the new work request, persisting state in WorkManager's own database. | [WorkManager: Unique Work](https://developer.android.com/topic/libraries/architecture/workmanager/how-to/unique-work) / [ExistingWorkPolicy](https://developer.android.com/reference/androidx/work/ExistingWorkPolicy) (Google / Android Developers) | WorkManager 2.x, API 31–36 | HIGH | WorkManager persistence operates in its own SQLite database and is not part of Room SQLite transactions. Execution timing depends on system scheduler constraints. | Consequence for later implementation prompt: rescheduling `AutoCompleteWorker` (+24h) via `REPLACE` replaces previous work requests safely once invoked, but cannot be rolled back if an accompanying Room transaction fails later. |
| **T-CAL-13** | DOCUMENTED FACT | CalendarProvider operations document standard Android runtime failure modes: `SecurityException` (permission missing/revoked), `IllegalArgumentException` (invalid or missing column values), and generic IPC failures (`RemoteException` wrapped by ContentResolver). | [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver) / [CalendarContract](https://developer.android.com/reference/android/provider/CalendarContract) (Google / Android Developers) | API 31–36 | HIGH | ContentResolver API contracts document these exceptions; specific OEM behavior on proprietary sync adapters may yield varying exception subclasses. | Consequence for later implementation prompt: defensive error handling around ContentResolver calls is necessary to prevent unhandled exceptions from propagating to core app flows. |
| **T-CAL-14** | INFERENCE | Cross-system coordination options (such as ordering Room writes before or after CalendarProvider IPC, using compensation logic, or background reconciliation) are design inferences rather than platform guarantees. Because cross-provider atomic transactions do not exist, any chosen sequence inherently trades off specific failure modes (e.g. orphan calendar events vs missing event IDs in Room). | Deduced from T-CAL-06, T-CAL-09, T-CAL-10, T-CAL-13, and MASTER_SPEC SP-058/SP-068 | CallUpp architecture | HIGH | No single sequence achieves hardware 2PC atomicity on Android without external distributed transaction coordinators. | Consequence for later implementation prompt: architecture and sequencing decisions must be explicitly determined by the Control Plane rather than assumed by the researcher. |
| **T-CAL-15** | DOCUMENTED FACT | Android API 31–36 platform specifics: CalendarProvider ContentProvider authority (`com.android.calendar`) is part of system core and does not require package visibility `<queries>` declaration for ContentResolver queries. Android 15+ (API 35) introduces Private Space, which strictly separates accounts and CalendarProvider instances between main and private profiles. TargetSdk 36 requires strict adherence to runtime permissions and modern Coroutines/Dispatchers (e.g. `Dispatchers.IO` for ContentResolver IPC). | [Package Visibility](https://developer.android.com/training/package-visibility) / [Android 15 Private Space](https://developer.android.com/about/versions/15/features#private-space) / [Coroutines on Android](https://developer.android.com/kotlin/coroutines) (Google / Android Developers) | API 31–36 | HIGH | ContentResolver calls perform synchronous disk/IPC operations and must never run on the main (UI) thread (`Dispatchers.Main`). | Implementation prompt must execute all ContentResolver queries, inserts, updates, and deletes on `Dispatchers.IO`. |

---

## WHAT IS AUTHORITATIVELY KNOWN

1. **Independent Transaction Domains**:
   - Room `@Transaction` operates strictly on local SQLite database transactions (`PRAGMA`, `BEGIN TRANSACTION`, `COMMIT`, `ROLLBACK`).
   - `CalendarProvider` is a separate system process accessed via Binder IPC through `ContentResolver`.
   - `WorkManager` maintains its own dedicated internal SQLite database (`androidx.work.workdatabase`).
   - Android provides no distributed transaction manager, two-phase commit (2PC), or cross-provider atomic transaction mechanism bridging Room, CalendarProvider, and WorkManager.

2. **CalendarProvider Contract & Constraints**:
   - Inserting an event requires `CALENDAR_ID`, `DTSTART`, `DTEND` (for non-recurring events), and `EVENT_TIMEZONE`.
   - A valid writable `CALENDAR_ID` must be queried from `CalendarContract.Calendars` where `CALENDAR_ACCESS_LEVEL >= 500` (`CAL_ACCESS_CONTRIBUTOR`).
   - Inserting returns a `Uri` with the numeric ID parsed via `ContentUris.parseId(uri)` as a `Long`, which maps directly to `JobEntity.calendarEventId`.
   - For non-recurring 60-minute events (SP-046), `DTEND = DTSTART + 3600000L` and `DURATION` must remain null.
   - Updating or deleting via `ContentResolver.update`/`delete` using `ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)` returns the affected row count as `Int`. If the event was deleted externally, it returns `0` rows affected without throwing an exception.

3. **Permission Requirements & Failure Modes**:
   - `READ_CALENDAR` and `WRITE_CALENDAR` are runtime dangerous permissions.
   - Missing or revoked permissions cause `ContentResolver` to throw `java.lang.SecurityException`.
   - IPC failures or remote process crashes cause `RemoteException` / `DeadObjectException` (wrapped as runtime exceptions).
   - Missing mandatory fields throw `IllegalArgumentException`.

4. **WorkManager Guarantees**:
   - `WorkManager.enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE` reliably cancels existing pending work with that unique name and schedules the new request in WorkManager's own persistent store.
   - WorkManager runs independently of the app process lifecycle and triggers via system `JobScheduler` even if the app process has died and restarted.

---

## WHAT IS INFERENCE

1. **Interpretation of SP-056 ("w jednej Room transaction")**:
   - SP-056 states: *"Następujące akcje wykonywać w jednej Room transaction: Accept AI term: Job term, Calendar update jeśli dotyczy, reschedule AutoCompleteWorker, AiSuggestion accepted."*
   - [INFERENCE]: Because external IPC operations (Calendar ContentResolver and WorkManager) physically cannot participate in an SQLite transaction, this specification requirement represents a platform boundary:
     - The database state changes (`JobEntity.confirmedStartAt`, `JobEntity.updatedAt`, `JobEntity.calendarEventId`, and `AiSuggestionEntity.status = ACCEPTED`) execute within the Room SQLite transaction.
     - CalendarProvider and WorkManager operations are separate subsystem invocations whose exact coordination, compensation, or retry pattern must be established by the Control Plane.

2. **Coordination and Compensation Trade-offs**:
   - [INFERENCE]: Application-layer sequencing options each present distinct failure trade-offs:
     - **Option 1 (Database first, best-effort external update)**: Commit Room transaction, then attempt Calendar update and WorkManager reschedule. If Calendar fails, core data remains valid, but the external calendar may be out of sync.
     - **Option 2 (External update first, compensating rollback)**: Update CalendarProvider first; if Room commit subsequently fails, attempt a compensating delete or revert of the Calendar event. If process death intervenes, an orphan external change remains.
     - **Option 3 (Outbox / Reconciliation pattern)**: Persist an intention or sync state in Room, with a background worker reconciling CalendarProvider state.
   - Any selection among these patterns is an architectural decision for the Control Plane, not a direct mandate of the Android platform.

3. **Orphan Handling & Idempotency Considerations**:
   - [INFERENCE]: If process death occurs after a Calendar operation succeeds but before Room persists the event ID, an orphan event may exist in the system calendar. Possible mitigations include querying existing events by time window/title before inserting, or allowing manual user reconciliation.
   - [INFERENCE]: `AutoCompleteWorker` (+24h) should verify current state at execution time (verifying `status == ACTIVE`, `deletedAt == null`, and current time `>= confirmedStartAt + 24h`) rather than assuming immutable scheduling.

---

## WHAT REMAINS UNKNOWN

1. **OEM Calendar Provider Variations**:
   - Different Android device manufacturers (Samsung, Xiaomi, Google Pixel, OnePlus) ship proprietary CalendarProvider backends or custom synchronization adapters. While standard AOSP `CalendarContract` tables and columns are required by the Android Compatibility Definition Document (CDD), OEM-specific behavior regarding local non-synced calendars (e.g. "My Phone", "Samsung Calendar") when no Google Account is logged in may vary.
2. **Sync Adapter Latency and Conflict Resolution**:
   - When CallUpp updates an event in CalendarProvider while the device is offline or during an active background sync with Google Calendar servers, the exact conflict resolution applied upstream by Google Calendar Sync Adapter is proprietary to the sync provider.
3. **Multi-Calendar User Preference**:
   - If a user has multiple writable calendars (e.g. personal Gmail, work Exchange, local calendar), CallUpp's heuristic of selecting the first writable primary/visible calendar (`IS_PRIMARY == 1`, `CALENDAR_ACCESS_LEVEL >= 500`) may select a calendar the user did not intend unless a user preference setting is introduced in future versions.

---

## PRODUCT REQUIREMENTS THAT MUST NOT CHANGE

The following canonical requirements from `docs/core/MASTER_SPEC.md` are immutable and must not be altered:

1. **Manual Confirmation Only (SP-016, SP-046)**:
   - Calendar event creation occurs *only* after manual user confirmation (`Potwierdź termin i dodaj do kalendarza`). AI and incoming SMS never create Calendar events automatically (SP-001 #9, SP-034).
2. **Fixed Duration v1 (SP-046)**:
   - Event duration is fixed internally at 60 minutes (`DTEND = DTSTART + 60 * 60 * 1000L`). No duration picker is exposed in overlay or full job screen.
3. **Owned Event Lifecycle (SP-046)**:
   - If `calendarEventId` exists, term modification must update the existing event.
   - Soft-delete of a Job (`deletedAt != null`) must delete the owned Calendar event.
4. **AI Term Update Flow (SP-034, SP-056, SP-057 Flow H)**:
   - When a user accepts an AI proposed term change for a job with a confirmed Calendar event: the event is updated, the job term is updated, and the `AutoCompleteWorker` (+24h) is rescheduled.
5. **Fail-Safe Isolation & Stability (SP-058, SP-066 #19, SP-068)**:
   - Calendar permission denial, missing calendar, or CalendarProvider exceptions must NEVER crash the app and must NEVER prevent creating/saving a note, client, or job.
   - Calendar is an adapter around the core workflow `ROZMOWA → NOTATKA → KLIENT → ZLECENIE`.

---

## QUESTIONS FOR CONTROL PLANE

1. **Transaction Coordination Policy for SP-056 (`Accept AI term`)**:
   - Given that Room SQLite transactions cannot natively encapsulate IPC calls to `CalendarProvider` or `WorkManager`:
   - How should the implementation sequence the updates across Room, CalendarProvider, and WorkManager?
     - Approach A: Commit Room transaction first (Job term + AI suggestion status), then invoke CalendarProvider and WorkManager best-effort with error logging.
     - Approach B: Invoke CalendarProvider first; if successful, commit Room; if Room fails, attempt compensating revert on CalendarProvider.
     - Approach C: Store pending sync state in Room and delegate external updates to a persistent background worker.

2. **Handling Externally Deleted Events (`update` returns 0 rows)**:
   - If an event is deleted externally, a subsequent `ContentResolver.update` returns 0 rows affected.
   - Should CallUpp:
     - (A) Clear `calendarEventId = null` on the Job; OR
     - (B) Clear `calendarEventId = null` and display a non-blocking informational notice on the Full Job screen?

3. **Behavior When Zero Writable Calendars Exist**:
   - If `READ_CALENDAR` / `WRITE_CALENDAR` are granted, but no writable calendar exists (`CALENDAR_ACCESS_LEVEL >= 500` returns empty):
   - What is the expected UI feedback when the user taps `Potwierdź termin i dodaj do kalendarza` while confirming the job term in Room?
