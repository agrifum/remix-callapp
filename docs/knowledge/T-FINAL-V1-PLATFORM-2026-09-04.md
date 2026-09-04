# CallUpp V1 Final Platform Evidence (2026-09-04)

Document ID: T-FINAL-V1-PLATFORM-2026-09-04
Repository: agrifum/remix-callapp
Base SHA: 7434f24b7f78c5164db3e8d09237f77ca6a81fb8

---

## 1. Verified Dependencies & Versions

1. **Room 3 Stable**
   - Coordinates: androidx.room3:room3-runtime:3.0.2, androidx.room3:room3-compiler:3.0.2
   - Registry: Google Maven (dl.google.com/dl/android/maven2)
   - Kotlin Multiplatform & Coroutines Flow first-class support.
   - Package namespace: androidx.room3.*.

2. **Navigation 3 Stable**
   - Coordinates: androidx.navigation3:navigation3-runtime:1.1.7, androidx.navigation3:navigation3-ui:1.1.7
   - Registry: Google Maven (dl.google.com/dl/android/maven2)
   - Type-safe navigation backstack architecture using explicit object/data class keys and NavDisplay.

3. **Dagger Hilt**
   - Coordinates: com.google.dagger:hilt-android:2.60.1, com.google.dagger:hilt-compiler:2.60.1
   - Gradle Plugin: com.google.dagger.hilt.android:2.60.1
   - Registry: Maven Central.
   - Standard Android dependency injection architecture with @HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel.

4. **Firebase BoM & Firebase AI Logic**
   - Firebase BoM: 34.18.0
   - Coordinates: com.google.firebase:firebase-ai:17.16.0
   - Model: Gemini 2.5/3.5 Flash Lite
   - Features: Structured JSON Schema extraction, App Check integration.

5. **Android Platform Telephony Contract (SP-049)**
   - CallUpp is an overlay assistant, NOT a default phone dialer.
   - CallScreeningService provides incoming and outgoing phone number and direction metadata.
   - TelephonyCallback / PhoneStateListener provides global audio state (RINGING -> OFFHOOK -> IDLE).
   - Platform constraint: Remote answer detection on outgoing calls is not exposed to non-default dialers. CallUpp strictly treats OFFHOOK as active call lifecycle, matching MASTER_SPEC §49.

---

## 2. External Configuration Requirements & Fail-Closed Boundaries

1. **Firebase / Google Services Configuration**
   - Requires google-services.json or runtime Firebase Options.
   - When external configuration is missing at runtime, FirebaseSmsExtractionEngine must fail-closed safely without crashing the host application.
   - Deterministic testing must utilize FakeSmsExtractionEngine.

2. **System Roles & Permissions**
   - RoleManager.ROLE_CALL_SCREENING: Explicit user request intent via RoleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING).
   - Permissions: READ_PHONE_STATE, READ_CALL_LOG, RECEIVE_SMS, SYSTEM_ALERT_WINDOW, POST_NOTIFICATIONS.
   - Onboarding flow must guide user through these configurations on first launch.
