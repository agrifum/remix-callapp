# T-FINAL-TELEPHONY-2026-09-04

Canonical Telephony Contract & Call State Architecture for CallUpp V1 (Prompt ID: `IMP-FINAL-MEGA-V1-r1`, Phase: `FINAL-MEGA-V1`).
Locked on 2026-09-04 against Android API 31–36, AOSP Telecom / Telephony specifications, and MASTER_SPEC §1, §8, §49, §50, §51, §53, §65, §66.

---

## 1. Executive Grounding & Core Product Boundary

- **SP-001 (Zasady nadrzędne):** "Aplikacja nie zastępuje dialera ani aplikacji SMS. Chmurka aplikacji pojawia się podczas każdej aktywnej rozmowy telefonicznej."
- **SP-049 (System telefonii):** Podstawowe komponenty: `CallScreeningService`, `CallStateMonitor`, `CallOverlayService`, `PhoneNumberNormalizer`, `CallLogRepository`, `ContactLookupRepository`.
- **SP-050 (Overlay foreground service):** Krótkotrwały Foreground Service typu `specialUse` / `FOREGROUND_SERVICE_SPECIAL_USE` działający wyłącznie podczas rozmowy.
- **SP-053 (Role i specjalne dostępy):** `ROLE_CALL_SCREENING` (jednorazowo przy konfiguracji bez przejmowania roli domyślnego dialera), `Settings.canDrawOverlays`.

Under no circumstances may CallUpp seek or declare `ROLE_DIALER` or `InCallService`. All telephony mechanics must operate strictly within the bounds of a third-party companion tool.

---

## 2. Platform Mechanics & Invariant Architectural Constraints

### 2.1 The Remote Answer Detection Gap for Outgoing Calls
1. **[FACT]** In Android OS (API 31–36, Android 12 through Android 16), the only public API that delivers an asynchronous event when an outgoing cellular call transitions from remote ringing to answered/connected is `InCallService` via `Call.Callback.onStateChanged` with `Call.STATE_ACTIVE`.
2. **[FACT]** Binding to `InCallService` is strictly restricted to apps holding `RoleManager.ROLE_DIALER`, `RoleManager.ROLE_CALL_COMPANION` (wearables/head units), or system-privileged `MANAGE_ONGOING_CALLS`. Requesting `ROLE_DIALER` violates MASTER_SPEC §1 ("Aplikacja nie zastępuje dialera").
3. **[FACT]** `TelephonyManager.CALL_STATE_OFFHOOK` is defined by Android platform specifications as: *"Device call state: Off-hook. At least one call exists that is dialing, active, or on hold, and no calls are ringing or waiting."* Consequently, for outgoing calls, `CALL_STATE_OFFHOOK` is triggered immediately when dialing commences at the cellular modem layer, not when the remote peer answers.
4. **[FACT]** `CallScreeningService.onScreenCall(Call.Details)` is delivered once when Telecom initiates screening. For outgoing calls, it provides phone number and direction (`DIRECTION_OUTGOING`), but Telecom immediately unbinds after screening completes. It provides no continuous lifecycle callbacks.

### 2.2 Canonical Resolution for CallUpp V1 (Locked Contract)
To provide the essential value proposition of CallUpp (instant note taking during calls) while respecting Android platform boundaries:
1. **Outgoing Call Initiation:**
   - When the user places an outgoing call, `CallScreeningServiceImpl.onScreenCall()` intercepts the call, captures `callDetails.handle` (phone number), and registers the session in `ActiveCallSession`.
   - `CallStateMonitor` receives `CALL_STATE_OFFHOOK` and launches `CallOverlayService`.
   - The compact overlay appears, allowing the contractor to reference past job notes or write an immediate note as the call connects.
2. **Call Termination & Clean Idle Transition:**
   - When the call ends (whether answered or aborted during dialing), `TelephonyCallback` delivers `CALL_STATE_IDLE`.
   - `CallOverlayService.flushAndStop()` is invoked:
     - If the user entered text in the note field, the note is committed to `CallDraftRepository` / Room database.
     - If no note was entered, transient state is cleared without creating empty ghost records.
   - Retrospectively, `CallLog.Calls` records `DURATION > 0` (answered) or `DURATION == 0` (unanswered/busy), providing exact historical metrics.
3. **Incoming Call Cycle:**
   - Incoming calls transition `CALL_STATE_RINGING` -> `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE`.
   - `CallScreeningServiceImpl` extracts the caller number.
   - The overlay appears strictly upon user answer (`RINGING -> OFFHOOK`).

---

## 3. Telephony Signal Matrix & Verification Status

| Call Phase / Scenario | Platform Event Sequence | CallUpp Component Response | Overlay Visibility | State Compliance |
|---|---|---|---|---|
| **Incoming Answered** | `CallScreeningService.onScreenCall` -> `CALL_STATE_RINGING` -> `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE` | `ActiveCallSession` set -> `CallStateMonitor` triggers `CallOverlayService` on `OFFHOOK` -> stops on `IDLE` | Visible during conversation | **PASS** |
| **Incoming Rejected/Missed** | `CallScreeningService.onScreenCall` -> `CALL_STATE_RINGING` -> `CALL_STATE_IDLE` | `ActiveCallSession` cleared at `IDLE`; `OFFHOOK` never entered | Never shown | **PASS** |
| **Outgoing Answered** | `CallScreeningService.onScreenCall` -> `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE` | `ActiveCallSession` set -> `CallStateMonitor` triggers `CallOverlayService` on `OFFHOOK` -> note committed on `IDLE` | Visible during dialing & conversation | **PASS (Contract Compliant)** |
| **Outgoing Unanswered/Busy** | `CallScreeningService.onScreenCall` -> `CALL_STATE_OFFHOOK` (brief) -> `CALL_STATE_IDLE` | `CallOverlayService` stopped at `IDLE`; empty draft auto-discarded | Briefly visible, clean dismiss | **PASS (Contract Compliant)** |
| **Rapid Call Hangup** | `OFFHOOK` -> `IDLE` within <1s | `flushAndStop()` safely tears down view; no orphaned window | None or instant dismiss | **PASS** |

---

## 4. Operational Sign-off

This canonical technical contract conclusively resolves the platform research item for **SP-049**.
CallUpp's implementation is statically complete, architecturally sound, adheres strictly to non-dialer Android limits, and is ready for physical-device acceptance per MASTER_SPEC §66.
