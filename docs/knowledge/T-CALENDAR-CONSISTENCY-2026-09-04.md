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
| **T-CAL-11** | DOCUMENTED FACT | The Android OS may terminate the application process at any instant between statements (via `LowMemoryKiller`, user task swipe, system reboot, or unhandled exception). If process death occurs between a Room write and a Calendar operation, or between Calendar insert and Room write, the system is left in an uncoordinated partial state. | [Processes and Application Lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle) (Google / Android Developers) | API 1+ (verified API 31–36) | HIGH | Process termination without warning is a fundamental architectural reality of Android app execution. | Implementation prompt must sequence operations and define crash recovery: (1) Calendar created first + immediate Room commit + compensation delete on failure; or (2) Room stores pending sync status + background worker reconciles. |
| **T-CAL-12** | DOCUMENTED FACT | `WorkManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequest)` persists work requests in its own internal SQLite database (`androidx.work.workdatabase`). When using `ExistingWorkPolicy.REPLACE`, it cancels and deletes any existing unfinished work chain with that unique name and enqueues the new work request. | [WorkManager: Unique Work](https://developer.android.com/topic/libraries/architecture/workmanager/how-to/unique-work) / [ExistingWorkPolicy](https://developer.android.com/reference/androidx/work/ExistingWorkPolicy) (Google / Android Developers) | WorkManager 2.x, API 31–36 | HIGH | WorkManager does not participate in Room transactions. However, once `enqueueUniqueWork` returns successfully, the request is safely persisted to disk and managed by system `JobScheduler`. | Implementation prompt for SP-056 / SP-057 Flow H: Rescheduling `AutoCompleteWorker` (+24h) with `ExistingWorkPolicy.REPLACE` keyed by unique name `"autocomplete_job_${jobId}"` is reliable once called. Worker execution must inspect Room at runtime to verify job is still `ACTIVE`. |
| **T-CAL-13** | DOCUMENTED FACT | CalendarProvider / ContentResolver operations document specific failure modes: `SecurityException` (missing/revoked permission or insufficient calendar access), `IllegalArgumentException` (missing mandatory columns or invalid URI format), `IllegalStateException` (provider initialization failure), `RemoteException` / `DeadObjectException` (CalendarProvider process crashed during IPC, wrapped as RuntimeException by ContentResolver). | [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver) / [CalendarProvider2.java](https://android.googlesource.com/platform/packages/providers/CalendarProvider/+/master/src/com/android/providers/calendar/CalendarProvider2.java) (Google / AOSP) | API 31–36 | HIGH | Any IPC call to ContentResolver can theoretically throw runtime exceptions if the remote provider process is killed or unresponsive. | Implementation prompt must wrap all CalendarProvider interactions in defensive `runCatching` blocks. Per SP-058 and SP-068, Calendar failure must never abort or block Room persistence of notes, clients, or jobs. |
| **T-CAL-14** | INFERENCE | To satisfy SP-056, SP-058, and SP-068 without cross-provider transactions: The recommended execution sequence for `Accept AI term` and manual confirmation is: (1) Execute Room transaction updating `JobEntity` (and `AiSuggestionEntity` status); (2) Attempt CalendarProvider update/insert in a separate try/catch; (3) If Calendar succeeds, persist `calendarEventId` to Room; if Calendar throws `SecurityException` or fails, log failure and leave Room intact; (4) Enqueue WorkManager `AutoCompleteWorker` with `ExistingWorkPolicy.REPLACE`. | Deduced from T-CAL-06, T-CAL-09, T-CAL-10, T-CAL-13, and MASTER_SPEC SP-058/SP-068 | CallUpp architecture | HIGH | Application-level sequencing cannot guarantee 100% two-phase atomicity across process death, but satisfies SP-068 ("Calendar jest adapterem... jego awaria nie może zaburzać podstawowego przepływu"). | Implementation prompt should adopt this resilient sequencing. If process dies after Calendar insert but before Room saves ID, an orphan event exists; on next edit or view, user can re-confirm or app can reconcile. |
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
   - [INFERENCE]: Because external IPC operations (Calendar ContentResolver and WorkManager) physically cannot participate in an SQLite transaction, this specification requirement must be interpreted at the application layer as:
     - The database state changes (`JobEntity.confirmedStartAt`, `JobEntity.updatedAt`, `JobEntity.calendarEventId`, and `AiSuggestionEntity.status = ACCEPTED`) execute within the Room transaction.
     - CalendarProvider and WorkManager operations are coordinated immediately alongside the Room transaction with defensive error isolation and compensating actions if any stage fails.

2. **Sequencing and Compensation Strategy**:
   - [INFERENCE]: To satisfy SP-058 ("Calendar permission odmówione... nie może uniemożliwić zapisu zlecenia") and SP-068 ("jego awaria nie może zaburzać podstawowego przepływu"), the recommended sequencing is:
     - **Manual Confirmation / Create Event**: Check permissions. If granted and writable calendar exists, call `ContentResolver.insert`. If successful, write `calendarEventId` into Room inside the Job confirmation transaction. If Calendar insert fails or throws `SecurityException`, log the warning, keep `calendarEventId = null`, and still commit the Job confirmation in Room.
     - **Update Term**: Update `JobEntity` in Room. If `job.calendarEventId != null` and permissions are granted, call `ContentResolver.update`. If `update` returns `0` (event was externally deleted), clear `calendarEventId` in Room or mark for user re-confirmation.
     - **Soft-Delete Job**: Mark Job soft-deleted in Room (`deletedAt = now`). If `job.calendarEventId != null`, attempt `ContentResolver.delete`. Whether `delete` returns `1` or `0`, clear `calendarEventId` in Room.
     - **Accept AI Term**: Inside Room transaction, update Job term, mark AI suggestion accepted. Outside the SQLite lock (or immediately following), execute Calendar update and `WorkManager.enqueueUniqueWork("autocomplete_${jobId}", ExistingWorkPolicy.REPLACE, ...)`.

3. **Orphan Handling & Idempotency**:
   - [INFERENCE]: If process death occurs after `Calendar.insert` succeeds but before Room persists `calendarEventId`, an orphan event remains in the user's system calendar. This is benign from a data corruption standpoint (does not corrupt SQLite database), but duplicate events could be created if the user re-confirms. A light reconciliation check (e.g. querying calendar by title and time window) or simply allowing manual user adjustment is sufficient.
   - [INFERENCE]: `AutoCompleteWorker` must be idempotent: when executed, it must query `JobDao.getJobById(jobId)` and verify `status == ACTIVE`, `deletedAt == null`, and current time is `>= confirmedStartAt + 24h` before marking `COMPLETED`.

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

1. **Transaction Ordering & Failure Policy for SP-056 (`Accept AI term`)**:
   - Given that Room SQLite transactions cannot natively encapsulate IPC calls to `CalendarProvider` or `WorkManager`:
     - *Preferred Option A (Database First, Adapter Resilient)*: Room transaction updates Job term and AI suggestion status first. Then Calendar update is attempted via `runCatching`. If Calendar update fails (e.g. permission revoked, event deleted externally), the failure is logged or flagged, but the Room transaction remains committed.
     - *Preferred Option B (External First, Compensated)*: Calendar update is attempted first; if successful, Room transaction commits; if Room transaction fails, a compensating Calendar rollback is attempted.
     - *Recommendation*: Option A directly aligns with SP-058 and SP-068 (core job data must never fail due to adapter failure). Does Control Plane confirm Option A as the canonical interpretation?

2. **Handling Externally Deleted Events (`update` returns 0 rows)**:
   - If a user deletes an event directly in the Google Calendar app, CallUpp's subsequent `ContentResolver.update` call for that `calendarEventId` will return 0 rows affected.
   - Should CallUpp:
     - (A) Silently clear `calendarEventId = null` on the Job and treat it as no longer attached to a calendar event; OR
     - (B) Clear `calendarEventId = null` and display a non-blocking UI notice on the Full Job screen ("Wydarzenie kalendarza nie zostało znalezione")?

3. **Fallback When Zero Writable Calendars Exist**:
   - If the user has granted `READ_CALENDAR` / `WRITE_CALENDAR`, but the device has no writable calendar accounts configured (`CALENDAR_ACCESS_LEVEL >= 500` returns empty):
   - Should the UI button `Potwierdź termin i dodaj do kalendarza` show a friendly error/toast ("Brak dostępnego kalendarza w systemie") while still allowing manual confirmation of the job term in Room?
