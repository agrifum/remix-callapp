# TEST-MATRIX-V1

Canonical one-row-per-bullet matrix for MASTER_SPEC §65. The specification contains
**57 literal items**. `PASS` means an automated test exercises production logic;
`PHYSICAL` is reserved for carrier/OEM/device behavior and is not claimed here.

| # | §65 item | Evidence | Status |
|---:|---|---|---|
| 1 | incoming answered | CallHandlingAndOverlayCharacterizationTest | PASS |
| 2 | incoming rejected | CallHandlingAndOverlayCharacterizationTest | PASS |
| 3 | outgoing answered | CallHandlingAndOverlayCharacterizationTest | PASS |
| 4 | outgoing unanswered | CallHandlingAndOverlayCharacterizationTest | PASS |
| 5 | rapid call end | CallHandlingAndOverlayCharacterizationTest | PASS |
| 6 | unknown number | CallDraftPersistenceCharacterizationTest | PASS |
| 7 | contact number | CallDraftPersistenceCharacterizationTest | PASS |
| 8 | autosave | CallHandlingAndOverlayCharacterizationTest | PASS |
| 9 | process killed | CallDraftCommitIdempotencyTest | PASS |
| 10 | IME | required deterministic test not present | GAP |
| 11 | rotation/window change | required deterministic test not present | GAP |
| 12 | screen lock/unlock | physical lifecycle verification | PHYSICAL |
| 13 | multiple active notes | required deterministic test not present | GAP |
| 14 | create from number | CallDraftPersistenceCharacterizationTest | PASS |
| 15 | Contacts name | ClientDetailAndTagsCharacterizationTest | PASS |
| 16 | manual edit | PhoneNumberNormalizerCharacterizationTest | PASS |
| 17 | one number only | required deterministic test not present | GAP |
| 18 | address update | JobLifecycleCharacterizationTest | PASS |
| 19 | first job | JobLifecycleCharacterizationTest | PASS |
| 20 | multiple jobs | JobMultiSelectionActionCharacterizationTest | PASS |
| 21 | blank preliminary term | JobLifecycleCharacterizationTest | PASS |
| 22 | duplicate term warning | JobLifecycleCharacterizationTest | PASS |
| 23 | active -> completed | JobLifecycleCharacterizationTest | PASS |
| 24 | active -> closed | JobLifecycleCharacterizationTest | PASS |
| 25 | reopen | ReengagementAtomicityCharacterizationTest | PASS |
| 26 | new based on previous | required deterministic test not present | GAP |
| 27 | global OFF | SmsAiGatingCharacterizationTest | PASS |
| 28 | client OFF | SmsAiGatingCharacterizationTest | PASS |
| 29 | no ACTIVE jobs | SmsAiGatingCharacterizationTest | PASS |
| 30 | one ACTIVE | required deterministic test not present | GAP |
| 31 | multiple ACTIVE | required deterministic test not present | GAP |
| 32 | SMS before job | required deterministic test not present | GAP |
| 33 | SMS between jobs | required deterministic test not present | GAP |
| 34 | SMS after completion | required deterministic test not present | GAP |
| 35 | resume with new analysis window | required deterministic test not present | GAP |
| 36 | invalid JSON | SmsAiGatingCharacterizationTest | PASS |
| 37 | no network | FirebaseSmsExtractionEngineTest | PASS |
| 38 | empty address | required deterministic test not present | GAP |
| 39 | filled address | required deterministic test not present | GAP |
| 40 | same address | required deterministic test not present | GAP |
| 41 | new address | required deterministic test not present | GAP |
| 42 | empty term | required deterministic test not present | GAP |
| 43 | same term | required deterministic test not present | GAP |
| 44 | changed term | required deterministic test not present | GAP |
| 45 | calendar create | CalendarIntegrationCharacterizationTest | PASS |
| 46 | calendar update | required deterministic test not present | GAP |
| 47 | calendar delete | CalendarIntegrationCharacterizationTest | PASS |
| 48 | calendar permission denied | required deterministic test not present | GAP |
| 49 | Maps installed | ManualEtaCharacterizationTest | PASS |
| 50 | ETA parsed | required deterministic test not present | GAP |
| 51 | parser fails | required deterministic test not present | GAP |
| 52 | notification access denied | required deterministic test not present | GAP |
| 53 | manual arrival HH:MM | ManualEtaCharacterizationTest | PASS |
| 54 | complete multiple | JobMultiSelectionActionCharacterizationTest | PASS |
| 55 | archive multiple | JobMultiSelectionActionCharacterizationTest | PASS |
| 56 | delete multiple | JobMultiSelectionActionCharacterizationTest | PASS |
| 57 | restore from trash | JobMultiSelectionActionCharacterizationTest | PASS |

**Automated PASS:** 33/57. **Deterministic gaps:** 23. **Physical split:** 1
(screen lock/unlock), with real carrier/OEM behavior explicitly not simulated.
This matrix is not a release gate until every GAP is covered.
