# TEST-MATRIX-V1

Canonical one-row-per-bullet matrix for MASTER_SPEC §65. The specification contains
**57 literal items**. `UNIT_PASS`/`ROBOLECTRIC_PASS`/`INSTRUMENTED_PASS` indicate automated evidence strength; `PHYSICAL_PENDING` is reserved for carrier/OEM/device behavior and is not claimed here.

| # | §65 item | Evidence | Status |
|---:|---|---|---|
| 1 | incoming answered | CallHandlingAndOverlayCharacterizationTest | UNIT_PASS |
| 2 | incoming rejected | CallHandlingAndOverlayCharacterizationTest | UNIT_PASS |
| 3 | outgoing answered | CallHandlingAndOverlayCharacterizationTest | UNIT_PASS |
| 4 | outgoing unanswered | CallHandlingAndOverlayCharacterizationTest | UNIT_PASS |
| 5 | rapid call end | CallHandlingAndOverlayCharacterizationTest | UNIT_PASS |
| 6 | unknown number | CallDraftPersistenceCharacterizationTest | UNIT_PASS |
| 7 | contact number | CallDraftPersistenceCharacterizationTest | UNIT_PASS |
| 8 | autosave | CallHandlingAndOverlayCharacterizationTest | UNIT_PASS |
| 9 | process killed | CallDraftCommitIdempotencyTest | UNIT_PASS |
| 10 | IME | required deterministic test not present | GAP |
| 11 | rotation/window change | required deterministic test not present | GAP |
| 12 | screen lock/unlock | physical lifecycle verification | PHYSICAL_PENDING |
| 13 | multiple active notes | required deterministic test not present | GAP |
| 14 | create from number | CallDraftPersistenceCharacterizationTest | UNIT_PASS |
| 15 | Contacts name | ClientDetailAndTagsCharacterizationTest | UNIT_PASS |
| 16 | manual edit | PhoneNumberNormalizerCharacterizationTest | UNIT_PASS |
| 17 | one number only | required deterministic test not present | GAP |
| 18 | address update | JobLifecycleCharacterizationTest | UNIT_PASS |
| 19 | first job | JobLifecycleCharacterizationTest | UNIT_PASS |
| 20 | multiple jobs | JobMultiSelectionActionCharacterizationTest | UNIT_PASS |
| 21 | blank preliminary term | JobLifecycleCharacterizationTest | UNIT_PASS |
| 22 | duplicate term warning | JobLifecycleCharacterizationTest | UNIT_PASS |
| 23 | active -> completed | JobLifecycleCharacterizationTest | UNIT_PASS |
| 24 | active -> closed | JobLifecycleCharacterizationTest | UNIT_PASS |
| 25 | reopen | ReengagementAtomicityCharacterizationTest | UNIT_PASS |
| 26 | new based on previous | required deterministic test not present | GAP |
| 27 | global OFF | SmsAiGatingCharacterizationTest | UNIT_PASS |
| 28 | client OFF | SmsAiGatingCharacterizationTest | UNIT_PASS |
| 29 | no ACTIVE jobs | SmsAiGatingCharacterizationTest | UNIT_PASS |
| 30 | one ACTIVE | required deterministic test not present | GAP |
| 31 | multiple ACTIVE | required deterministic test not present | GAP |
| 32 | SMS before job | required deterministic test not present | GAP |
| 33 | SMS between jobs | required deterministic test not present | GAP |
| 34 | SMS after completion | required deterministic test not present | GAP |
| 35 | resume with new analysis window | required deterministic test not present | GAP |
| 36 | invalid JSON | SmsAiGatingCharacterizationTest | UNIT_PASS |
| 37 | no network | FirebaseSmsExtractionEngineTest | UNIT_PASS |
| 38 | empty address | required deterministic test not present | GAP |
| 39 | filled address | required deterministic test not present | GAP |
| 40 | same address | required deterministic test not present | GAP |
| 41 | new address | required deterministic test not present | GAP |
| 42 | empty term | required deterministic test not present | GAP |
| 43 | same term | required deterministic test not present | GAP |
| 44 | changed term | required deterministic test not present | GAP |
| 45 | calendar create | CalendarIntegrationCharacterizationTest | UNIT_PASS |
| 46 | calendar update | required deterministic test not present | GAP |
| 47 | calendar delete | CalendarIntegrationCharacterizationTest | UNIT_PASS |
| 48 | calendar permission denied | required deterministic test not present | GAP |
| 49 | Maps installed | ManualEtaCharacterizationTest | UNIT_PASS |
| 50 | ETA parsed | required deterministic test not present | GAP |
| 51 | parser fails | required deterministic test not present | GAP |
| 52 | notification access denied | required deterministic test not present | GAP |
| 53 | manual arrival HH:MM | ManualEtaCharacterizationTest | UNIT_PASS |
| 54 | complete multiple | JobMultiSelectionActionCharacterizationTest | UNIT_PASS |
| 55 | archive multiple | JobMultiSelectionActionCharacterizationTest | UNIT_PASS |
| 56 | delete multiple | JobMultiSelectionActionCharacterizationTest | UNIT_PASS |
| 57 | restore from trash | JobMultiSelectionActionCharacterizationTest | UNIT_PASS |

**Automated evidence rows in §65 matrix:** 33/57 (UNIT/ROBOLECTRIC). **Deterministic gaps:** 23. **Physical pending:** 1
(screen lock/unlock), with real carrier/OEM behavior explicitly not simulated.
Runtime startup smoke coverage is tracked separately by `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` and CI `runtime-startup` gate.
This matrix is not a release gate until every GAP is covered.
