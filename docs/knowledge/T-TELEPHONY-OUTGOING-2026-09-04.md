# T-TELEPHONY-OUTGOING-2026-09-04

Technical baseline and platform signal research for Phase RSCH-TELEPHONY (Prompt ID: `RSCH-TELEPHONY-OUTGOING-r2`, CP: `CP-RSCH-TELEPHONY-r2`).
Investigated against official Android developer documentation, Android Telecom API specifications, and Android Open Source Project (AOSP) references on 2026-09-04.

---

## Question

On Android API 31–36 / targetSdk 36, for a private sideloaded application using the call-screening role (`ROLE_CALL_SCREENING`) and declared CallUpp permissions (`READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`), what officially supported signals can reliably establish:

- incoming vs outgoing direction;
- outgoing number identity;
- outgoing answered vs unanswered;
- actual active-call state;
- transition back to idle;

so that overlay appears for real active calls without replacing the default dialer?

---

## Product SP IDs

- **SP-001 (Zasady nadrzędne):** Aplikacja nie zastępuje dialera ani aplikacji SMS. Chmurka aplikacji pojawia się podczas każdej aktywnej rozmowy telefonicznej.
- **SP-008 (OVERLAY — podstawowy widok podczas rozmowy):** Overlay pojawia się dopiero, gdy rozmowa faktycznie trwa (`OFFHOOK`). Nie musi być widoczny podczas samego dzwonienia.
- **SP-049 (System telefonii):** Podstawowe komponenty: `CallScreeningService`, `CallStateMonitor`, `CallOverlayService`, `PhoneNumberNormalizer`, `CallLogRepository`, `ContactLookupRepository`. `OFFHOOK` -> uruchom overlay; `IDLE` -> zakończ overlay.
- **SP-050 (Overlay foreground service):** Krótko żyjący Foreground Service (`specialUse` / `FOREGROUND_SERVICE_SPECIAL_USE`) działający wyłącznie podczas rozmowy.
- **SP-051 (Uprawnienia — wymagane):** `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
- **SP-053 (Role i specjalne dostępy):** `ROLE_CALL_SCREENING` (jednorazowo przy konfiguracji bez przejmowania roli domyślnego dialera), Draw over other apps (`Settings.canDrawOverlays`).
- **SP-057 (Główne przepływy):** FLOW A (zwykła rozmowa: `OFFHOOK` -> overlay -> notatka -> Zapisz -> `IDLE` -> overlay znika), FLOW J (reengagement event przy ponownym kontakcie).
- **SP-058 (Stabilność):** Bezawaryjne działanie bez crasha przy braku opcjonalnych uprawnień, restarcie procesu lub systemu.
- **SP-061 (Pozycja overlay):** Górna część ekranu poniżej systemowego status bara, bez zasłaniania przycisków dialera (mute, speaker, end call).
- **SP-062 (Focus i klawiatura overlay):** Panel domyślnie non-focusable; przejęcie focusu i IME dopiero po tapnięciu pola notatki.
- **SP-065 (Testy obowiązkowe):** Call handling obejmujący: `incoming answered`, `incoming rejected`, `outgoing answered`, `outgoing unanswered`, `rapid call end`, `unknown number`, `contact number`.
- **SP-066 (Definition of Done v1):** Wykrywanie przychodzących i wychodzących rozmów oraz pokazywanie overlay po rozpoczęciu rozmowy na fizycznym urządzeniu.

---

## Current repository signal path

The current repository handles telephony via four components in `app/src/main/java/com/example/system/calls/`:

1. **`CallScreeningServiceImpl`:**
   - Declared in `AndroidManifest.xml` with `android.permission.BIND_SCREENING_SERVICE`.
   - Telecom invokes `onScreenCall(callDetails: Call.Details)` on incoming and outgoing call placement.
   - Extracts phone number via `callDetails.handle?.schemeSpecificPart` and direction via `callDetails.callDirection == Call.Details.DIRECTION_OUTGOING`.
   - Writes snapshot to in-memory `ActiveCallSession.setCall(...)`.
   - For incoming calls, responds immediately via `respondToCall(...)`. For outgoing calls, exits without responding (as `respondToCall` is incoming-only).
2. **`CallStateMonitor`:**
   - Registers `TelephonyCallback` implementing `TelephonyCallback.CallStateListener` on `TelephonyManager` using `mainExecutor` (requires `READ_PHONE_STATE`).
   - Receives integer state callbacks:
     - `CALL_STATE_RINGING`: Flags `currentDirection = INCOMING` and initializes session ID.
     - `CALL_STATE_OFFHOOK`: Resolves direction (prefers `ActiveCallSession.get()?.direction`, fallback to checking whether prior state was `RINGING`), resolves phone number from `ActiveCallSession`, and immediately launches `CallOverlayService` via `startForegroundService` (`ACTION_SHOW_OVERLAY`).
     - `CALL_STATE_IDLE`: Clears `ActiveCallSession`, invokes `CallOverlayService.flushAndStop(...)`, commits uncommitted note draft to Room via `CallDraftRepository`, and checks re-engagement.
3. **`ActiveCallSession`:**
   - In-memory `StateFlow<ActiveCall?>` synchronizing `phoneKey`, `rawNumber`, `displayNumber`, and `CallDirection`.
4. **`PhoneStateReceiver`:**
   - Broadcast receiver listening to `ACTION_PHONE_STATE_CHANGED`. Redundant legacy path operating concurrently with `CallStateMonitor`.
5. **`CallLogRepository`:**
   - Queries `CallLog.Calls` to populate the filtered call history UI. Does not participate in real-time call onset detection.

### Inherent Architectural Tension
In the current implementation, `CallStateMonitor` triggers `CallOverlayService` immediately upon receiving `CALL_STATE_OFFHOOK`. For outgoing calls, Android transitions to `CALL_STATE_OFFHOOK` at dialing start. As a consequence, the overlay appears before the recipient answers, displaying during dialing and appearing even for unanswered/busy outgoing attempts.

---

## Official evidence table

| Claim ID | Category | Finding | Official URL / Title | API / Version | Confidence | Limitation | Consequence for later implementation prompt |
|---|---|---|---|---|---|---|---|
| **T-TEL-01** | DOCUMENTED FACT | `RoleManager.ROLE_CALL_SCREENING` allows an application to screen calls and provide caller ID metadata without becoming the default dialer (`ROLE_DIALER`). | [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_CALL_SCREENING) | API 29+ (verified API 31–36) | HIGH | Requires runtime user consent via `RoleManager.createRequestRoleIntent`; only one screening app active at a time. | CallUpp can obtain call metadata while remaining within the non-dialer boundary mandated by SP-001 and SP-053. |
| **T-TEL-02** | DOCUMENTED FACT | `CallScreeningService.onScreenCall(Call.Details)` is called for both incoming and outgoing calls. `Call.Details.getCallDirection()` returns `DIRECTION_INCOMING` (1) or `DIRECTION_OUTGOING` (2). | [CallScreeningService.onScreenCall](https://developer.android.com/reference/android/telecom/CallScreeningService#onScreenCall(android.telecom.Call.Details)) & [Call.Details.getCallDirection](https://developer.android.com/reference/android/telecom/Call.Details#getCallDirection()) | API 24+ (`getCallDirection` API 29+; targetSdk 36) | HIGH | Called once when the call is placed/added; the framework unbinds after screening completes. It provides no ongoing lifecycle or connected callbacks. | Call direction is established authoritatively at call onset via `CallScreeningService`. |
| **T-TEL-03** | DOCUMENTED FACT | `CallScreeningService` receives the call number via `callDetails.getHandle()`. However, Telecom explicitly filters out calls to/from contacts in the user's address book unless the service holds `Manifest.permission.READ_CONTACTS`. For outgoing calls, post-dial digits are omitted. | [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService) (Class Overview) | API 24+ (enforced API 31–36) | HIGH | Without `READ_CONTACTS`, outgoing calls to existing contacts are never delivered to `CallScreeningService`. Private/restricted numbers return null handle or `PRESENTATION_RESTRICTED`. | `READ_CONTACTS` is a strict operational prerequisite for `CallScreeningService`. If missing, outgoing contact numbers cannot be intercepted at call initiation. |
| **T-TEL-04** | INFERENCE / PLATFORM LIMITATION | `Call.Details` in `CallScreeningService` is delivered once at call placement/screening. While `Call.Details` defines accessors like `getConnectTimeMillis()`, official documentation for `CallScreeningService` notes it is invoked before a call is placed or accepted. At placement time `getConnectTimeMillis()` is not documented to reflect future remote connection, and `CallScreeningService` provides no connection-lifecycle callbacks. | [CallScreeningService.onScreenCall](https://developer.android.com/reference/android/telecom/CallScreeningService#onScreenCall(android.telecom.Call.Details)) | API 24+ | MEDIUM | `Call.Details` lifecycle callbacks (`Call.Callback`) belong to the in-call subsystem, not `CallScreeningService`. Once screening finishes, the service has no documented mechanism to observe when the call is answered. | `CallScreeningService` cannot be relied upon to detect when an outgoing call is answered or connected. |
| **T-TEL-05** | DOCUMENTED FACT | `TelephonyManager.CALL_STATE_OFFHOOK` is defined as: "Device call state: Off-hook. At least one call exists that is dialing, active, or on hold, and no calls are ringing or waiting." | [TelephonyManager.CALL_STATE_OFFHOOK](https://developer.android.com/reference/android/telephony/TelephonyManager#CALL_STATE_OFFHOOK) | API 1+ (`TelephonyCallback` API 31–36) | HIGH | For outgoing calls, `CALL_STATE_OFFHOOK` fires when dialing starts. There is NO documented state transition or callback in `TelephonyCallback.CallStateListener` when the remote party answers; the state remains `CALL_STATE_OFFHOOK` throughout. | `TelephonyManager` cannot distinguish between outgoing dialing and outgoing active conversation. |
| **T-TEL-06** | DOCUMENTED FACT | `TelephonyCallback.CallStateListener` replaces deprecated `PhoneStateListener` (API 31+) and requires `READ_PHONE_STATE`. It supplies only an integer state (`IDLE`, `RINGING`, `OFFHOOK`). | [TelephonyCallback.CallStateListener](https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener) | API 31+ | HIGH | Provides zero number, URI, or direction metadata. Direction must be inferred from the state transition sequence (`RINGING -> OFFHOOK` vs direct `IDLE -> OFFHOOK`) or correlated with `CallScreeningService`. | Telephony callback acts purely as an edge-triggered state synchronizer, dependent on `ActiveCallSession` for number and identity data. |
| **T-TEL-07** | DOCUMENTED FACT | `Intent.ACTION_NEW_OUTGOING_CALL` was deprecated in API 29 and broadcast delivery is blocked. `ACTION_PHONE_STATE_CHANGED` is deprecated in API 31; its extra `EXTRA_INCOMING_NUMBER` was deprecated in API 29, requires `READ_CALL_LOG`, and never provides outgoing numbers. | [ACTION_NEW_OUTGOING_CALL](https://developer.android.com/reference/android/content/Intent#ACTION_NEW_OUTGOING_CALL) & [ACTION_PHONE_STATE_CHANGED](https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED) | API 29+ / API 31+ | HIGH | Broadcast receivers cannot intercept outgoing numbers or outgoing call placement on modern Android. | `PhoneStateReceiver` in the repository is deprecated and incapable of capturing outgoing call numbers. It should be retired in favor of `CallScreeningService`. |
| **T-TEL-08** | DOCUMENTED FACT | `InCallService` provides full real-time call states (`STATE_DIALING`, `STATE_CONNECTING`, `STATE_ACTIVE`, `STATE_DISCONNECTED`) and callbacks via `Call.Callback.onStateChanged`. However, binding requires `ROLE_DIALER`, `ROLE_CALL_COMPANION` (wearable companion), or privileged permission `MANAGE_ONGOING_CALLS`. | [InCallService](https://developer.android.com/reference/android/telecom/InCallService) & [MANAGE_ONGOING_CALLS](https://developer.android.com/reference/android/Manifest.permission#MANAGE_ONGOING_CALLS) | API 23+ / API 30+ / API 31–36 | HIGH | Prohibited by SP-001 and SP-049 ("CallUpp must not become the default dialer"); CallUpp is a phone app, not a companion wearable app. | The standard documented Android API providing real-time `STATE_ACTIVE` transition signal is unavailable within CallUpp's non-dialer boundary. |
| **T-TEL-09** | INFERENCE | Documented `CallLog.Calls` provider schemas include final metrics such as `DURATION` (seconds). In practice, Android Telecom writes completed call log rows when a call disconnects, meaning real-time queries during an ongoing call cannot be relied upon to determine whether an outgoing call in progress has been answered. | [CallLog.Calls.DURATION](https://developer.android.com/reference/android/provider/CallLog.Calls#DURATION) | API 1+ | HIGH | Row insertion timing is an implementation characteristic of the system telephony/telecom stack rather than an explicit real-time streaming contract. | `CallLog` can be queried reliably after disconnect (`CALL_STATE_IDLE`) for retrospective analysis (`DURATION > 0` vs `DURATION == 0`), but cannot provide real-time answer detection during the call. |
| **T-TEL-10** | PLATFORM LIMITATION | On Android API 31–36 (targetSdk 36), there is NO documented public Android API available to a non-default-dialer application that delivers an event when an outgoing cellular call is answered by the remote party. | AOSP Telecom / Telephony Architecture & Android API Reference | API 31–36 | HIGH | The platform does not expose remote-answer events outside the in-call subsystem (`InCallService`). Non-dialer apps only observe coarse `CALL_STATE_OFFHOOK`. | Detecting "actual active-call state" distinct from "dialing" for outgoing calls without replacing the default dialer is not supported by documented Android APIs. |
| **T-TEL-11** | DOCUMENTED FACT | Multi-SIM devices report aggregated device-level call state on default `TelephonyManager`. Per-subscription monitoring requires `TelephonyManager.createForSubscriptionId(subId)`. `CallScreeningService` provides `callDetails.getAccountHandle()` identifying the SIM account. | [TelephonyManager.createForSubscriptionId](https://developer.android.com/reference/android/telephony/TelephonyManager#createForSubscriptionId(int)) & [Call.Details.getAccountHandle](https://developer.android.com/reference/android/telecom/Call.Details#getAccountHandle()) | API 24+ / API 31+ | HIGH | Default `TelephonyCallback` transitions to `CALL_STATE_OFFHOOK` if ANY SIM is active. | Default single-listener architecture must be correlated with `CallScreeningService` account handle if multi-SIM disambiguation is required. |
| **T-TEL-12** | INFERENCE | SP-008's requirement ("Overlay pojawia się dopiero, gdy rozmowa faktycznie trwa (OFFHOOK), nie musi być widoczny podczas samego dzwonienia") encounters a platform limitation for outgoing calls: `CALL_STATE_OFFHOOK` begins at dialing onset, so showing the overlay on `OFFHOOK` causes it to appear during dialing. | Deduction from T-TEL-05, T-TEL-08, T-TEL-10, and `MASTER_SPEC.md` §8 | API 31–36 | HIGH | A product decision is required by Control Plane regarding how to handle the dialing phase. | No fallback strategy is selected here; the platform gap is reported to the Control Plane. |

---

## Scenario matrix

| Scenario | Documented signals available | Direction source | Number source | Active-call source | Known gap |
|---|---|---|---|---|---|
| **incoming answered** | `CallScreeningService.onScreenCall`, `TelephonyCallback` `CALL_STATE_RINGING` -> `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE`, `CallLog.Calls` (`INCOMING_TYPE`, `DURATION > 0`). | `Call.Details.getCallDirection()` (`DIRECTION_INCOMING`) + `RINGING` state preceding `OFFHOOK`. | `Call.Details.getHandle()` (`tel:...`) via `CallScreeningService`. | `CALL_STATE_OFFHOOK` (transition from `RINGING` confirms user answered). | None. 100% reliable with documented APIs. |
| **incoming rejected** | `CallScreeningService.onScreenCall`, `TelephonyCallback` `CALL_STATE_RINGING` -> `CALL_STATE_IDLE` (never enters `OFFHOOK`), `CallLog.Calls` (`REJECTED_TYPE` or `MISSED_TYPE`, `DURATION == 0`). | `Call.Details.getCallDirection()` (`DIRECTION_INCOMING`) + `RINGING` state. | `Call.Details.getHandle()` via `CallScreeningService`. | None (never reaches `CALL_STATE_OFFHOOK`). | None. Overlay does not launch; state reset at `CALL_STATE_IDLE`. |
| **outgoing answered** | `CallScreeningService.onScreenCall`, `TelephonyCallback` `CALL_STATE_IDLE` -> `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE`, `CallLog.Calls` post-disconnect (`OUTGOING_TYPE`, `DURATION > 0`). | `Call.Details.getCallDirection()` (`DIRECTION_OUTGOING`) + absence of `RINGING` before `OFFHOOK`. | `Call.Details.getHandle()` via `CallScreeningService` (requires `READ_CONTACTS` if number is in contacts). | `CALL_STATE_OFFHOOK`. **CRITICAL GAP:** Fires at dialing commencement, NOT at remote answer. | Platform cannot signal remote answer to a non-dialer. Overlay either appears during dialing or cannot appear automatically during conversation. Post-call `CallLog` confirms answer retrospectively (`DURATION > 0`). |
| **outgoing unanswered** | `CallScreeningService.onScreenCall`, `TelephonyCallback` `CALL_STATE_IDLE` -> `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE`, `CallLog.Calls` post-disconnect (`OUTGOING_TYPE`, `DURATION == 0`). | `Call.Details.getCallDirection()` (`DIRECTION_OUTGOING`) + direct transition to `OFFHOOK`. | `Call.Details.getHandle()` via `CallScreeningService` (requires `READ_CONTACTS` if in contacts). | `CALL_STATE_OFFHOOK` is briefly entered during dialing. | If overlay launches at `CALL_STATE_OFFHOOK`, it briefly appears during dialing before call aborts. Mitigation: At `CALL_STATE_IDLE`, verify `CallLog.Calls.DURATION == 0` and purge uncommitted draft notes. |
| **rapid end** | Rapid transition `CALL_STATE_OFFHOOK` -> `CALL_STATE_IDLE` (< 1–2 seconds). | `CallScreeningService` or state transition history. | `CallScreeningService.onScreenCall`. | `CALL_STATE_OFFHOOK`. | Potential race condition where `CallOverlayService` is started and immediately stopped before view attaches. Handled by `flushAndStop` and auto-committing only non-empty drafts. |
| **unknown number** | `CallScreeningService.onScreenCall` delivered unconditionally (not filtered by contact rules). | `Call.Details.getCallDirection()`. | `Call.Details.getHandle()` unless caller ID is suppressed (`PRESENTATION_RESTRICTED` / `UNKNOWN`). | `CALL_STATE_OFFHOOK`. | Suppressed caller ID yields null handle; overlay must handle null `phoneKey` gracefully (cannot create client or job without number). |
| **contact number** | `CallScreeningService.onScreenCall` delivered ONLY IF `READ_CONTACTS` is granted. `TelephonyCallback` unaffected. | `Call.Details.getCallDirection()`. | `Call.Details.getHandle()` (if `READ_CONTACTS` granted). | `CALL_STATE_OFFHOOK`. | Without `READ_CONTACTS`, Telecom privacy filtering completely bypasses `CallScreeningService` for all contact numbers; CallUpp cannot intercept the number at call initiation. |

---

## Synthesis & conclusions

### SUPPORTED WITH DOCUMENTED API

1. **Direction detection:** Determined reliably by `Call.Details.getCallDirection()` (`DIRECTION_INCOMING` vs `DIRECTION_OUTGOING`) in `CallScreeningService.onScreenCall()`. Secondarily validated by `TelephonyCallback.CallStateListener` state sequence (`RINGING -> OFFHOOK` for incoming, direct `IDLE -> OFFHOOK` for outgoing).
2. **Number identity for non-contact numbers:** Intercepted reliably via `Call.Details.getHandle()` in `CallScreeningService.onScreenCall()`.
3. **Number identity for contact numbers:** Intercepted reliably via `Call.Details.getHandle()` in `CallScreeningService.onScreenCall()` **if and only if** `Manifest.permission.READ_CONTACTS` is granted to the application.
4. **Active incoming call detection:** Transition from `CALL_STATE_RINGING` to `CALL_STATE_OFFHOOK` reliably confirms the user has answered the incoming call.
5. **Call termination:** Transition to `CALL_STATE_IDLE` in `TelephonyCallback.CallStateListener` reliably signals the end of all call activity.
6. **Retrospective call disposition:** Querying `CallLog.Calls` at `CALL_STATE_IDLE` using `READ_CALL_LOG` reliably confirms whether the finished call was answered (`DURATION > 0`) or unanswered/rejected (`DURATION == 0`).
7. **Role acquisition:** `RoleManager.ROLE_CALL_SCREENING` is officially documented and available from API 29 through API 36 without dialer replacement.

### INFERENCE REQUIRED

1. **Session correlation:** Correlating `CallScreeningService.onScreenCall()` with subsequent `TelephonyCallback.CallStateListener` events requires an in-memory session manager (`ActiveCallSession`) keyed by time and direction, because `TelephonyCallback` does not pass call identifiers or numbers.
2. **Treating `CALL_STATE_OFFHOOK` as active for outgoing calls:** Because the platform does not notify when the remote party answers, an application wishing to display an in-call overlay during outgoing calls must infer/assume the call is active upon entering `CALL_STATE_OFFHOOK` (accepting that this includes the dialing phase).
3. **Post-call draft cleanup:** If an outgoing call terminates with `DURATION == 0` (unanswered/busy) and the user has not actively typed or saved note text, the system must infer that the call was aborted and discard the transient draft rather than committing an empty note.

### UNSUPPORTED / UNKNOWN (PLATFORM LIMITATION)

1. **Real-time outgoing answered detection for non-dialers (PLATFORM LIMITATION):**
   Android API 31–36 / targetSdk 36 provides **NO documented public API** that notifies a third-party non-default-dialer app when an outgoing call transitions from dialing/ringing to answered/connected.
   - `InCallService` supports `Call.STATE_ACTIVE`, but strictly requires `ROLE_DIALER`, `ROLE_CALL_COMPANION` (wearables), or `MANAGE_ONGOING_CALLS` (privileged/system). CallUpp is prohibited from becoming the default dialer by SP-001 and SP-049.
   - `TelephonyManager.CALL_STATE_OFFHOOK` covers dialing, active conversation, and on-hold collectively without intermediate events.
   - `CallScreeningService.onScreenCall()` fires only once upon call placement (`connectTimeMillis` is 0).
   - `CallLog.Calls` is not written until call termination.
2. **Outgoing contact number interception without `READ_CONTACTS` (PLATFORM LIMITATION):**
   The Android Telecom subsystem explicitly suppresses `CallScreeningService` callbacks for numbers present in the device contacts unless `READ_CONTACTS` is granted. There is no alternative real-time API to intercept outgoing contact numbers without this permission.

---

## Questions for control plane

1. **Outgoing Overlay Trigger Policy:**
   Given that Android does not provide a signal for remote answer to non-dialer apps, how should CallUpp handle the dialing phase for outgoing calls?
   - Possibility A: Trigger overlay upon `CALL_STATE_OFFHOOK` for outgoing calls (overlay appears during dialing). If the call terminates with `DURATION == 0` and no user input was made, the draft is discarded.
   - Possibility B: Defer overlay or note prompt until post-call disconnect (`CALL_STATE_IDLE`) conditional on `DURATION > 0` in `CallLog`.
   - Possibility C: Launch in collapsed state during `CALL_STATE_OFFHOOK` requiring explicit interaction.
2. **`READ_CONTACTS` Onboarding Requirement:**
   Because Telecom omits `CallScreeningService` delivery for contact numbers without `READ_CONTACTS`, should onboarding treat `READ_CONTACTS` as required for full telephony support rather than optional?
3. **Redundant `PhoneStateReceiver`:**
   Should an implementation prompt remove legacy `PhoneStateReceiver.kt` from the codebase, consolidating telephony monitoring solely into `CallStateMonitor` and `CallScreeningServiceImpl`?
