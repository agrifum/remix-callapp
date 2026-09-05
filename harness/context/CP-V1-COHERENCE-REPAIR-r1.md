# CP-V1-COHERENCE-REPAIR-r1

STATUS: READY
OWNER: CallUpp AI Control Plane
DATE: 2026-09-05
AUDIT BASE: `0d2d7914fb9d4ce214d86358bae38ce930fc9f4c`
EXECUTION BASE: must be pinned to the exact current PR #13 HEAD in the dispatch comment after this Control Plane commit lands.
PROMPT: `IMP-V1-COHERENCE-REPAIR-r1`
PHASE: `V1-COHERENCE-REPAIR`

## Purpose

This is the single bounded context pack for the pre-physical V1 coherence repair. It consolidates defects verified by the CallUpp AI Control Plane against the current repository and the canonical product source. It supersedes piecemeal future repair instructions, but does not rewrite historical prompts or evidence.

The source of truth controls WHAT. Current official Android/Firebase documentation controls HOW where API behavior is material. Do not weaken a requirement to make a test pass.

## Canonical sources to read

1. `AGENTS.md`
2. `docs/control/ENVIRONMENT_BOUNDARY.md`
3. `docs/control/PROJECT_RULES.md`
4. `docs/core/MASTER_SPEC.md` — authoritative product specification
5. `docs/evidence/SOURCE_SUPPLEMENT_WORKFLOW.md`
6. `docs/prompts/PROMPT_CONTRACT.md`
7. this context pack
8. current PR #13 diff/history and current exact execution HEAD

Do not use old audit PASS claims as authority over `MASTER_SPEC.md`.

## Current verified execution evidence

At audit base `0d2d791...`, PR #13 remains open/unmerged on `final/v1-completion-20260904`.

Android CI #65 (`33965849003`) failed before runtime instrumentation:
- `compileDebugKotlin` PASS;
- `assembleDebug` PASS;
- 113 JVM/Robolectric tests executed, 1 failed;
- failure: `SmsTriggerPrivacyAndWorkerTest.smsReceiver_eligibleSms_createsMetadataOnlyTrigger_andEnqueuesWorker` timed out while observing transient PENDING state;
- `runtime-startup` was skipped because it depends on `verify`.

The `verify` job also checks out GitHub's PR merge ref, while `runtime-startup` was already repaired to check out the exact PR head. This evidence inconsistency must be removed.

## Product invariants that must be preserved

- private/sideload, local-first; no CRM/cloud sync;
- Kotlin/JVM17, minSdk 31, compile/target 36;
- Compose Material3 main UI, Navigation 3, ViewModel + StateFlow + UDF;
- native View/XML `TYPE_APPLICATION_OVERLAY` through `CallOverlayService`;
- Room 3 + KSP, Hilt, DataStore, WorkManager;
- Firebase AI Logic only for one-SMS extraction/summary, structured output, fail-closed;
- no raw SMS body in Room;
- no autonomous AI job creation, SMS sending, or overwrite of approved data;
- no `SEND_SMS`, `CALL_PHONE`, location, recording, `WRITE_CONTACTS`, `QUERY_ALL_PACKAGES`;
- core Call → Note → Client → Job/history remains usable without AI/network/SMS permission/Calendar/notification listener;
- initial onboarding sequence remains: ROLE_CALL_SCREENING → READ_PHONE_STATE → overlay → POST_NOTIFICATIONS → READ_CALL_LOG → READ_CONTACTS;
- SMS, Calendar and notification-listener permissions/access stay deferred to first relevant use;
- Job status, archive and trash are independent concepts;
- physical/OEM/carrier claims remain pending until physical acceptance;
- do not merge PR #13.

## Verified defect set

### A. Data integrity and business semantics

A1. **Client uniqueness is unsafe.** `ClientDao.insertClient()` uses `OnConflictStrategy.REPLACE` while `phoneKey` is UNIQUE and Jobs reference Client with `ON DELETE CASCADE`. Duplicate normalized-number insertion can replace the existing Client identity and endanger dependent history. Implement safe normalized-phone upsert preserving the existing Client ID/history. Every Client creation path must use it. Add regression coverage for `+48` / `0048` / local equivalent → one Client and preserved Jobs/Notes.

A2. **Archive incorrectly changes ACTIVE business semantics.** Business queries for ACTIVE Jobs filter `isArchived=0`. Archive is independent of status. SMS eligibility, reengagement and other business rules must treat ACTIVE+not-deleted as active even when archived. UI visibility may still separate archived items. Add regressions.

A3. **Manual Client address edits do not propagate to ACTIVE Job snapshots.** Update Client plus snapshots of current ACTIVE Jobs transactionally; never rewrite completed/closed snapshots.

A4. **Job created from overlay bypasses immediate +24h scheduling.** After a successful transaction, schedule completion when the new Job has a completion anchor.

A5. **AI auto-fill of an empty Job term bypasses +24h rescheduling.** Schedule changed eligible Jobs after the DB transaction commits.

A6. **Accepted term suggestion can leave stale `confirmedStartAt`.** Unify preliminary/confirmed term semantics so accepted change updates the actually authoritative term, Calendar event where present, and completion schedule. Never silently convert a merely preliminary suggestion into a confirmed appointment.

A7. **Reengagement Resume/New transitions are not atomically coherent with event resolution.** Make the local DB transition atomic where feasible; execute external/work scheduling side effects after commit. Never leave a false PENDING event after a locally successful transition.

A8. **Note↔Task permanent-delete/30-day retention has an unresolved edge.** Task has FK cascade to Note while Note and Task have separate trash states and cleanup. Prevent demonstrably unintended silent data loss. If canonical sources do not determine the exact permanent-delete behavior when a surviving Task references a Note, record `U-COH-01` and stop only this subchange rather than inventing product behavior.

### B. UI completeness and required architecture

B1. **Client screen is incomplete:** wire New Job; active notes; tasks; filtered call history; archived notes. Honor `showClientTags` as display-only preference.

B2. **Number Detail is incomplete:** Android Contacts display name, tasks, archived notes and filtered call history are missing. Keep Job/AI/address/NIP out of this screen.

B3. **New Job for existing Client is incomplete:** preselect Client, current address, latest historical service and latest price; leave term, SMS summary and old notes empty; create initial analysis window and scheduling consistently.

B4. **Job Detail is incomplete:** complete editing of service/price/term and expose pending AI suggestions with explicit accept/ignore actions. Preserve approved-data protection.

B5. **Duplicate active term is warning-only.** It may warn during editing/AI, but final Calendar confirmation must be blocked until the conflict is resolved.

B6. **Services UI is incomplete:** existing service name/default price/active state must be editable. Deactivated services must remain manageable/reactivatable. Do not physically delete historically used services.

B7. **SMS templates are not connected to actual Client/Job SMS actions.** Add reorder and a minimal template-selection/substitution flow that prepares ACTION_SENDTO/smsto content. User still confirms in the SMS app; never auto-send.

B8. **Tasks manual-add flow writes fake phone `+48000000000`.** Remove placeholder persistence. A Task must derive from a real Note/phone context per §23. Task cards must show number/client, date, Call and DONE action as specified.

B9. **Note archive lifecycle lacks complete UI.** Add archive/unarchive controls and archived sections where required, separate from Trash.

B10. **Reengagement is surfaced primarily in Calls, not in the required Jobs/Client workflow.** Surface PENDING events in Jobs list / Client with Resume / New / Ignore, without duplicating events.

B11. **Required ViewModel + StateFlow + UDF architecture is not implemented for the main Compose UI.** Move screen state and mutations into Hilt ViewModels with immutable UI state/actions while preserving Navigation 3 and existing repositories/adapters. This is implementation of MASTER_SPEC §2, not a redesign. Keep navigation callbacks at UI boundary as appropriate.

### C. SMS / AI correctness

C1. Build AI local date/time from `trigger.receivedAt` in the applicable timezone, not Worker execution `now`, so relative dates are interpreted in message context.

C2. Existing-term protection must treat date-only, time-only, or confirmed term as existing; current code omits `preliminaryTimeMinute`.

C3. Align Firebase structured schema with parser requirements. Fields required by application validation must not be declared optional in a way that makes valid output contract ambiguous. Preserve unknown-jobId ignore and fail-closed validation.

C4. Enforce the source summary guardrail (~300–500 chars, avoid redundant address/term repetition unless contextually useful) in prompt/validation without inventing facts.

C5. When SMS AI is first enabled/used, request the required SMS permissions in a user-initiated flow; denial must leave core usable. Do not move them into initial onboarding.

C6. Repair CI #65's timing-sensitive SMS receiver test deterministically. Do **not** increase the timeout. Control WorkManager/TestDriver execution or assert stable durable effects while preserving proof that eligible metadata creates exactly one trigger/work request and no raw body is stored.

### D. Calendar / ETA / permissions

D1. Separate +24h completion anchor from Calendar appointment start. A date-only Job must not be silently confirmed at 23:59:59 or invented 09:00. Final Calendar confirmation requires a resolved explicit appointment time; event duration remains 60 minutes.

D2. Request READ/WRITE_CALENDAR on first user-initiated Calendar use; retry after grant; denial must not crash/block core.

D3. Honor `preferredCalendarId` when valid, with safe fallback to an available calendar. Do not remove the DataStore requirement.

D4. Calendar update/delete is best-effort today with swallowed failures and no reconciliation. Preserve non-blocking core behavior but provide a deterministic retry/reconciliation path for stale external events, using current Calendar Provider guidance.

D5. ETA listener currently writes to `activeJobs.firstOrNull()`. Associate navigation session locally with the Job that launched Maps and update only that still-eligible Job.

D6. Improve ETA best-effort parser for common remaining-time/arrival/distance forms including combined hour+minute durations; parser failure remains safe and manual fallback remains authoritative.

D7. Enabling automatic ETA must lead the user to notification-listener access when needed; denial leaves manual ETA available.

### E. Calls / overlay / lifecycle robustness

E1. If READ_PHONE_STATE was absent at Application startup, monitoring may never be registered after onboarding grant. Re-run the idempotent monitor registration after permission grant/resume.

E2. §58 process-loss recovery is not yet implemented: active call identity/session is memory-only and overlay service is `START_NOT_STICKY`. Using current authoritative Android APIs, persist only the minimum active-session/draft metadata necessary to recover an interrupted process/service, restore overlay only when a real call is still active/eligible, and prevent ghost overlays. Add deterministic automated coverage where technically valid; keep OEM/force-stop behavior PHYSICAL_PENDING where Android semantics prevent automation.

E3. Foreground-service start fallback can itself throw. Make both start attempts fail-safe so telephony/overlay adapter restrictions never crash the core app.

E4. Keep production overlay native XML/View + WindowManager. Do not replace it with Compose.

### F. Privacy, build, CI, evidence

F1. Audit Android backup/data extraction against local-first privacy. Current `allowBackup=true` plus sample/empty rules can cloud-back up business data. Configure rules so CallUpp business data are not cloud-backed up. If canonical sources do not resolve device-to-device transfer policy, record that narrow question rather than silently choosing a new product policy.

F2. Make the `verify` PR job also check out and assert the exact PR head. If merge-result compatibility is checked, keep it a separate evidence category; do not confuse merge-ref PASS with exact-head PASS.

F3. Remove hard-coded ADB path from runtime script; resolve from Android SDK environment / `command -v adb` and fail clearly if absent.

F4. On runtime success, log exact SHA, emulator/ADB ready state, exact instrumentation filter, actual executed target tests and final count/results. An exit code alone is insufficient evidence.

F5. Add pre-final automated release-variant packaging verification where credentials allow. An ephemeral CI signing key may prove packaging only; never invent or commit user release credentials and never label this §66.20 physical release acceptance.

F6. Close every deterministic/automatable §65 GAP with production-logic tests where technically possible. Keep true carrier/OEM/physical items pending. Evidence levels must remain explicit.

F7. Correct `PHYSICAL-ACCEPTANCE-V1.md` terminology/procedure mismatches: Task status `DONE`, canonical lowercase template variables, canonical `google.navigation:q=...`, and preserve TC-20 release APK requirement.

F8. Update audit/ledger/build-log evidence from actual results only. Never resurrect blanket `66/66`, `111/111`, or static-build-as-runtime claims.

F9. Audit dependencies on the exact head. Remove only dependencies proven unused and unnecessary; do not remove required adapters. Location remains forbidden. Do not add dependencies without first-party compatibility evidence.

## Technical evidence policy

Before implementing any material HOW involving Room conflict/upsert semantics, Android process/service recovery, runtime/special permissions, Calendar Provider, notification listener, backup/data extraction, WorkManager, Navigation 3 or Firebase AI Logic, verify the current stable API behavior against first-party Android/Firebase documentation matching the repository versions. Record URLs/check date in the handoff or a focused knowledge note. Technology may change HOW but not WHAT.

## Execution method

- Work from the exact dispatch HEAD on the existing branch/PR only.
- Before edits, map every A–F item to source requirement, current files and a failing/reproducing test or static proof.
- Use TDD for behavior changes: failing regression first where technically possible, then minimal production fix.
- Implement in internal coherent phases (data invariants → architecture/UI → AI/SMS → Calendar/ETA → lifecycle → CI/evidence), but do not use remote GitHub Actions as an iterative syntax checker.
- Run focused local tests after each internal phase where the environment permits.
- Before pushing the implementation, run full local verification. If local environment cannot execute a required command, report that explicitly instead of fabricating PASS.
- After the final implementation push, run one authoritative exact-head CI. If it fails, inspect the exact log once. One evidence-driven correction/re-run is allowed only if the defect is directly identified and locally reproducible; otherwise STOP and return the failure to Control Plane. No open-ended repair loop.
- Preserve unrelated user changes and all accepted runtime-harness fixes already present at the execution base.

## Required final automated verification

At minimum:
- `git diff --check`;
- debug Kotlin compilation and debug APK assembly;
- full JVM/Robolectric suite with no timing-dependent transient-state waits;
- `lintDebug`;
- androidTest APK assembly;
- exact-head emulator runtime-startup gate with the real production MainActivity bootstrap;
- release-variant compile/package evidence clearly distinguished from physical/release-signing acceptance;
- updated literal 57-row §65 evidence matrix.

Runtime startup acceptance requires actual emulator boot and actual execution/PASS of the targeted MainActivity launch/onboarding/recreation/rotation/cold-start tests. Do not infer this from build success.

## Non-goals / stop conditions

- no merge;
- no physical acceptance in this phase;
- no invented Firebase config, signing credentials, user data or product behavior;
- no replacement PR;
- no weakening tests to make CI green;
- no architecture/dependency redesign beyond what MASTER_SPEC and verified defects require;
- stop a subchange if a canonical product decision is genuinely missing; record it as UNRESOLVED instead of guessing;
- stop the whole task if the branch/base is wrong, source truth is unavailable, or implementation would require changing a product requirement.

## Expected end state / handoff

A successful automated handoff may say only `AUTOMATED_RUNTIME_VERIFIED + PHYSICAL_ACCEPTANCE_PENDING` if runtime evidence actually proves it. It must report: exact start/end SHAs; changed files by phase; regression tests added; complete local command results; exact Actions run/job IDs and runtime test count; remaining §65 physical items; external Firebase/signing configuration status; any UNRESOLVED; corrected audit/matrix/ledger/build-log state. Never call the app complete or release-ready before physical §66 acceptance.
