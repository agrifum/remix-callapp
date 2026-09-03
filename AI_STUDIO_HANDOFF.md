# AI Studio Handoff — CallUpp / ZLECENIE

## 1. Build Status
- **Target Platform**: Native Android (compileSdk 36, targetSdk 36, minSdk 31, Kotlin 2.0, JVM 17)
- **UI Toolkit**: Jetpack Compose + Material 3, edge-to-edge support
- **Architecture**: Single activity (`MainActivity`), manual dependency injection via `AppContainer`, MVVM with Room 3 + KSP and StateFlow unidirectional data flow
- **Compilation**: Successfully compiled (`compile_applet` passed cleanly, 0 errors, 0 unresolved references)

## 2. Implemented Features
- **Core Path (CALL -> NOTE -> CLIENT -> JOB)**:
  - Phone number normalization and keying (`com.example.core.phone.PhoneNumberNormalizer`).
  - Separation of Phone Number vs Client: numbers are not clients until explicitly converted.
  - One primary phone number and current address per client, supporting multiple concurrent active and historical jobs.
  - 3-tab Bottom Navigation (`Calls`, `Jobs`, `Tasks`) + dedicated screens: `ClientDetail`, `JobDetail`, `NumberDetail`, `NewJob`, `Settings`, `ServicesSettings`, `Trash`, `Simulator`.
- **Overlay Engine**:
  - `CallOverlayService` using `WindowManager` + `TYPE_APPLICATION_OVERLAY` + programmatic `ComposeView` with explicit Lifecycle, SavedStateRegistry, and ViewModelStore owners.
  - Floating and expanded modes: quick note taking, price quote calculator, quick SMS templates (via Intent to SMS app, never sending automatically).
  - Draft persistence across calls via `CallDraftRepository`.
- **Simulator Screen**:
  - Built-in UI testing harness simulating incoming/outgoing calls, phone state changes, quick note drafting, and client conversion directly inside the AI Studio web emulator.
- **Jobs & Status Lifecycle**:
  - Full lifecycle: `DRAFT` -> `NEW` -> `IN_PROGRESS` -> `COMPLETED` / `CANCELLED` -> `SOFT_DELETED`.
  - Service item catalog with price minor calculation (grosze / PLN).
  - Job terms (agreed date/time, flexible windows, client address snapshot).
  - Background `JobStatusReconciler` worker for automated status updates.
- **Tasks & Trash Recovery**:
  - Task management per job or standalone, checkbox completion, date filters (today, upcoming, all).
  - Soft-delete pattern with 30-day retention across jobs, notes, and tasks.
  - `TrashCleanupWorker` scheduled via `WorkManager` for daily automated cleanup.
- **Returning Clients / Reengagement**:
  - Detection of incoming calls or SMS from existing clients without active jobs.
  - User prompt (`ReengagementDialog`) to resume previous job or create a new job from client history.
- **SMS AI Integration Architecture**:
  - `SmsExtractionEngine` contract with safety rules:
    - Never modifies approved address or terms.
    - Never acts outside active job analysis windows (`JobAnalysisWindowEntity`).
    - Never creates jobs or sends SMS automatically.
    - Raw SMS text is never stored in Room.
  - `FakeSmsExtractionEngine` implementation for AI Studio testing and offline reliability.
  - Pending AI suggestions displayed on `JobDetailScreen` for explicit user approval or dismissal.
- **External Integrations (Zero-Permission / System Intents)**:
  - Navigation via Google Maps `Intent.ACTION_VIEW` (`geo:`/`google.navigation:`).
  - SMS sending via `Intent.ACTION_SENDTO` (`smsto:`).
  - Google Calendar event creation via `Intent.ACTION_INSERT` (`CalendarContract.Events`).
  - Notification listener service (`MapsNotificationListenerService`) for Maps ETA extraction.

## 3. Acceptance IDs Verified in AI Studio
- **CORE-01**: Phone key normalization and non-client caller handling.
- **CORE-02**: Client creation and binding with note history.
- **CORE-03**: Multiple jobs per client (draft, active, completed).
- **CORE-04**: Offline-first Room persistence and StateFlow reactivity.
- **JOB-01**: Job creation with services, prices, and address snapshots.
- **JOB-02**: Job status transitions and cancellation/completion flows.
- **JOB-03**: Soft delete and restore from Trash screen.
- **TASK-01**: Standalone and job-bound tasks with status toggling.
- **REENG-01**: Reengagement event logging and user decision dialog (resume vs new job).
- **AI-01**: AI suggestion safety invariants (bounded within analysis window, user approval required).
- **SIM-01**: In-app simulator for phone calls and overlay drafting.

## 4. Acceptance IDs Requiring Physical POCO / HyperOS Validation
The following items interact directly with Xiaomi HyperOS / Android OS system daemon behaviors that cannot be fully exercised inside the browser-based cloud emulator:
- **DEVICE-SYS-01**: Background `CallScreeningService` binding on incoming GSM ringing on HyperOS (requires battery saver set to "No restrictions" and autostart enabled in MIUI/HyperOS settings).
- **DEVICE-SYS-02**: `SYSTEM_ALERT_WINDOW` permission grant and overlay drawing over incoming full-screen stock dialer on HyperOS.
- **DEVICE-SYS-03**: Background-FGS restrictions on Android 15/16 (compileSdk 36) when `PhoneStateReceiver` launches `CallOverlayService` while the app process is backgrounded.
- **DEVICE-SYS-04**: Real incoming SMS extraction trigger via `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` on physical dual-SIM carrier networks.
- **DEVICE-SYS-05**: `NotificationListenerService` permission and real Google Maps turn-by-turn navigation ETA banner capture on device.

## 5. Limitations Specific to AI Studio Android Build
- The browser-based emulator runs without an active cellular GSM radio or telephony network; real phone calls and carrier SMS must be exercised using the in-app `SimulatorScreen` or `adb emu` commands on a local workstation.
- Android 14+ / 15 background service start restrictions require special permission (`Settings.canDrawOverlays`) and system intent flow which are implemented in code and UI toggles, ready for hardware validation.

## 6. Exact Future Seam for Replacing FakeSmsExtractionEngine with FirebaseSmsExtractionEngine
When exporting the project ZIP to Android Studio and configuring Firebase Vertex AI / Gemini:
1. Open `app/src/main/java/com/example/ai/SmsExtractionEngine.kt` to inspect the interface.
2. Add the Firebase Vertex AI dependency to `app/build.gradle.kts` (e.g., `com.google.firebase:firebase-vertexai`).
3. Create `FirebaseSmsExtractionEngine.kt` in `com.example.ai` implementing `SmsExtractionEngine`.
4. In `app/src/main/java/com/example/core/di/AppContainer.kt`:
   ```kotlin
   // Replace:
   val smsExtractionEngine: SmsExtractionEngine by lazy { FakeSmsExtractionEngine() }
   // With:
   val smsExtractionEngine: SmsExtractionEngine by lazy { FirebaseSmsExtractionEngine() }
   ```
5. No changes are required in `SmsAnalysisCoordinator`, `JobDetailScreen`, or Room database DAOs because all extraction and candidate safety checks remain fully decoupled behind the `SmsExtractionEngine` interface.
