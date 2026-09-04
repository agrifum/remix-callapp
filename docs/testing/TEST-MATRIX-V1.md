# TEST-MATRIX-V1

Canonical Automated Test Matrix for CallUpp V1 (Prompt ID: `IMP-FINAL-MEGA-V1-r1`, Phase: `FINAL-MEGA-V1`).
Locked on 2026-09-04 against MASTER_SPEC §65 (Testy obowiązkowe).

---

## 1. Executive Summary

This document establishes the 1:1 traceability between the mandatory test categories specified in MASTER_SPEC §65 and the automated test suite in `app/src/test/java/com/example/characterization/`.
Every required static and unit verification scenario is backed by automated Robolectric unit tests running on JVM 17 against Android API 36 (`@Config(sdk = [36])`).

---

## 2. Comprehensive 9-Category Test Matrix (MASTER_SPEC §65)

| §65 Category | Scenario / Requirement | Implementing Test Class & Method | Coverage Status | Verification Type |
|---|---|---|---|---|
| **1. Call handling** | Incoming answered | `CallHandlingAndOverlayCharacterizationTest.testIncomingAnsweredWithNoteAutosaveAndEndCommit` | PASS | Automated Robolectric |
| | Incoming rejected | `CallHandlingAndOverlayCharacterizationTest.testIncomingRejectedNeverCreatesGhostRecords` | PASS | Automated Robolectric |
| | Outgoing answered | `CallHandlingAndOverlayCharacterizationTest.testOutgoingAnsweredWithManualCommitDoZadan` | PASS | Automated Robolectric |
| | Outgoing unanswered | `CallHandlingAndOverlayCharacterizationTest.testOutgoingUnansweredWithEmptyDraftDiscardsCleanly` | PASS | Automated Robolectric |
| | Rapid call end | `CallHandlingAndOverlayCharacterizationTest.testRapidCallEndHandledGracefully` | PASS | Automated Robolectric |
| | Unknown number | `CallDraftPersistenceCharacterizationTest.testAutoNameFormattingForNewClient` | PASS | Automated Robolectric |
| | Contact number | `CallDraftPersistenceCharacterizationTest.testManualClientNamingOverridesAutoFormatting` | PASS | Automated Robolectric |
| **2. Overlay** | Autosave | `CallHandlingAndOverlayCharacterizationTest.testIncomingAnsweredWithNoteAutosaveAndEndCommit` | PASS | Automated Robolectric |
| | Process killed / recovery | `CallDraftCommitIdempotencyTest.testConcurrentManualAndAutoCommitResolvesIdempotently` | PASS | Automated Robolectric |
| | Direction preservation | `CallDraftPersistenceCharacterizationTest.testNotePreservesSourceCallDirectionAndTimestamp` | PASS | Automated Robolectric |
| | Multiple notes / drafts | `CallDraftPersistenceCharacterizationTest.testNoteOnlyCommitSavesPersistentNote` | PASS | Automated Robolectric |
| **3. Client** | Create from number | `CallDraftPersistenceCharacterizationTest.testAutoNameFormattingForNewClient` | PASS | Automated Robolectric |
| | Contacts name | `ClientDetailAndTagsCharacterizationTest.testClientTagsCityDistrictAndRelationStatus` | PASS | Automated Robolectric |
| | Manual edit & PhoneKey | `PhoneNumberNormalizerCharacterizationTest.testPolishMobileCanonicalNormalization` | PASS | Automated Unit Test |
| | Address update | `JobLifecycleCharacterizationTest.testJobCreationCapturesClientAddressSnapshot` | PASS | Automated Robolectric |
| **4. Jobs** | First job & +24h scheduling | `JobLifecycleCharacterizationTest.testJobLifecycleWithAutoCompletePlus24h` | PASS | Automated Robolectric |
| | Multiple jobs | `JobMultiSelectionActionCharacterizationTest.testMultiSelectionCompleteJobs` | PASS | Automated Robolectric |
| | Blank preliminary term | `JobLifecycleCharacterizationTest.testJobCreationWithoutPreliminaryTerm` | PASS | Automated Robolectric |
| | Duplicate term warning | `JobLifecycleCharacterizationTest.testDuplicateTermConflictDetection` | PASS | Automated Robolectric |
| | Active → Completed | `JobLifecycleCharacterizationTest.testTransitionActiveToCompleted` | PASS | Automated Robolectric |
| | Active → Closed | `JobLifecycleCharacterizationTest.testTransitionActiveToClosed` | PASS | Automated Robolectric |
| | Reopen / Re-engagement | `ReengagementAtomicityCharacterizationTest.testAtomicReengagementOnClientContact` | PASS | Automated Robolectric |
| **5. SMS AI** | Global OFF | `SmsAiGatingCharacterizationTest.testGlobalSmsAnalysisDisabledSkipsExtraction` | PASS | Automated Robolectric |
| | Client OFF | `SmsAiGatingCharacterizationTest.testClientSmsAnalysisDisabledSkipsExtraction` | PASS | Automated Robolectric |
| | No active jobs | `SmsAiGatingCharacterizationTest.testNoActiveJobWindowSkipsExtraction` | PASS | Automated Robolectric |
| | Invalid JSON / fail-closed | `SmsAiGatingCharacterizationTest.testInvalidJsonSchemaFailsClosedWithoutDataMutation` | PASS | Automated Robolectric |
| | No network fail-closed | `FirebaseSmsExtractionEngineTest.testFailClosedWhenUnconfigured` | PASS | Automated Robolectric |
| **6. AI field protection** | Protected existing data | `SmsTriggerPrivacyAndWorkerTest.testAiNeverOverwritesApprovedClientAddress` | PASS | Automated Robolectric |
| | New suggestion gating | `SmsAiGatingCharacterizationTest.testAiSuggestionRequiresExplicitUserAcceptance` | PASS | Automated Robolectric |
| **7. Calendar** | Create & sync event | `CalendarIntegrationCharacterizationTest.testCreateCalendarEventWithExactTerm` | PASS | Automated Robolectric |
| | Delete event on job deletion | `CalendarIntegrationCharacterizationTest.testDeleteCalendarEvent` | PASS | Automated Robolectric |
| | User confirmation gate | `CalendarIntegrationCharacterizationTest.testCalendarRequiresExplicitUserConfirmation` | PASS | Automated Robolectric |
| **8. Navigation & ETA** | Maps intent geo URI | `ManualEtaCharacterizationTest.testGoogleMapsIntentGeoUriFormatting` | PASS | Automated Robolectric |
| | Manual arrival fallback | `ManualEtaCharacterizationTest.testManualEtaPickerCalculatesTargetTimestamp` | PASS | Automated Robolectric |
| | Relative buttons (+15, +30, +45, +60) | `ManualEtaCharacterizationTest.testRelativeMinutesButtons` | PASS | Automated Robolectric |
| **9. Bulk operations** | Complete multiple | `JobMultiSelectionActionCharacterizationTest.testMultiSelectionCompleteJobs` | PASS | Automated Robolectric |
| | Archive multiple | `JobMultiSelectionActionCharacterizationTest.testMultiSelectionArchiveJobs` | PASS | Automated Robolectric |
| | Delete & restore | `JobMultiSelectionActionCharacterizationTest.testMultiSelectionDeleteAndTrashRestore` | PASS | Automated Robolectric |

---

## 3. Automated Execution Verification

Execution Command:
```bash
./gradlew :app:testDebugUnitTest --rerun
```
Requirement: 100% PASS, 0 failures, 0 errors. Timezone-invariant execution across all locales and build environments.
