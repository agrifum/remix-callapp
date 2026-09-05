# PHYSICAL-ACCEPTANCE-V1

Physical Device Acceptance Pack for CallUpp V1 (Prompt ID: `IMP-FINAL-MEGA-V1-r1`, Phase: `FINAL-MEGA-V1`).
Derived from MASTER_SPEC §66 (Definition of Done v1).

---

## 1. Scope & Execution Conditions

This pack details the 20 physical hardware test procedures required before final production rollout of CallUpp V1.
All 20 tests must be performed on a physical Android device (API 31–36, e.g. Android 12, 13, 14, 15, or 16) with:
- Active cellular SIM card (incoming/outgoing cellular call capability)
- Overlay permission (`Settings.canDrawOverlays`)
- Call screening role (`RoleManager.ROLE_CALL_SCREENING`)
- Local storage and Google Calendar Provider access
- Google Maps installed

---

## 2. The 20 Definition of Done (DoD) Test Cases

### TC-01: Inbound & Outbound Call Detection
- **Requirement:** Wykrywa przychodzące i wychodzące rozmowy (§66.1).
- **Procedure:**
  1. Make an incoming call from another phone to the test device.
  2. Verify that `CallScreeningService` intercepts the caller ID and `TelephonyCallback` transitions `RINGING` -> `OFFHOOK`.
  3. Place an outgoing call to another phone.
  4. Verify that `CallScreeningService` intercepts the dialed number and `TelephonyCallback` transitions `OFFHOOK`.
- **Pass Criteria:** Both call directions are correctly identified with normalized phone numbers.

### TC-02: Overlay Appearance on Active Call
- **Requirement:** Pokazuje overlay po rozpoczęciu rozmowy (§66.2).
- **Procedure:**
  1. Answer incoming call.
  2. Verify that `CallOverlayService` renders the compact floating card in the top portion of the screen below the status bar.
  3. Verify that native in-call controls (Mute, Speaker, End call) remain visible and accessible.
- **Pass Criteria:** Floating overlay appears promptly upon call answer without obstructing essential phone controls.

### TC-03: Rapid Disconnect Note Preservation
- **Requirement:** Zapisuje notatkę mimo nagłego zakończenia rozmowy (§66.3).
- **Procedure:**
  1. During an active call, type "Spotkanie jutro o 10:00" into the overlay note field.
  2. Without clicking "Zapisz", immediately hang up the call from the remote phone (or tap end call).
  3. Open CallUpp app -> check "Zadania" or "Szczegóły numeru".
- **Pass Criteria:** The note text is auto-committed to Room database upon `CALL_STATE_IDLE` with `NoteSource.CALL`.

### TC-04: Client Creation Flow
- **Requirement:** Pozwala stworzyć klienta (§66.4).
- **Procedure:**
  1. From a call card or number detail screen, tap "Dodaj jako klienta".
  2. Enter client name, address, and save.
- **Pass Criteria:** Client record is created in Room DB with unique ID, formatted phone display, and canonical phone key.

### TC-05: Job Creation Flow
- **Requirement:** Pozwala stworzyć zlecenie (§66.5).
- **Procedure:**
  1. On a client detail screen, tap "Nowe zlecenie".
  2. Select service, enter preliminary date and time, tap "Zapisz zlecenie".
- **Pass Criteria:** Job is created in `ACTIVE` status and associated with client and initial `JobAnalysisWindow`.

### TC-06: CallLog Query & Filtering
- **Requirement:** Filtruje CallLog zgodnie z wymaganiami (§66.6).
- **Procedure:**
  1. Open "Połączenia" tab.
  2. Verify recent calls appear with caller name (if contact or client) or formatted phone number.
- **Pass Criteria:** System CallLog queries execute smoothly without copying full history into Room.

### TC-07: Note Archiving Lifecycle
- **Requirement:** Poprawnie archiwizuje notatki (§66.7).
- **Procedure:**
  1. On Client or Number Detail screen, archive an existing note.
  2. Verify note moves to archived state; restore it and verify it returns to active list.
- **Pass Criteria:** `isArchived` flag toggles correctly; soft delete moves to Trash (30 days purge).

### TC-08: Task Management
- **Requirement:** Obsługuje zadania (§66.8).
- **Procedure:**
  1. From overlay, tap "Do zadań" with note text.
  2. Open "Zadania" tab in main app.
  3. Mark task as completed.
- **Pass Criteria:** Open task appears in tab, and toggling completion updates status to `DONE`.

### TC-09: Multiple Jobs per Client
- **Requirement:** Obsługuje wiele zleceń klienta (§66.9).
- **Procedure:**
  1. Add a second active job with a different date for an existing client.
  2. Verify client card lists both jobs independently.
- **Pass Criteria:** Both jobs remain distinct in Room DB with their own timelines.

### TC-10: Deterministic +24h Auto-Complete
- **Requirement:** Automatycznie kończy zlecenia +24 h (§66.10).
- **Procedure:**
  1. Create a job scheduled for yesterday (or trigger WorkManager test execution).
  2. Observe background worker transition.
- **Pass Criteria:** `JobEntity.status` transitions from `ACTIVE` to `COMPLETED` exactly 24h past scheduled term.

### TC-11: Reengagement Detection on Completed Jobs
- **Requirement:** Wykrywa ponowny kontakt (§66.11).
- **Procedure:**
  1. Ensure a client has only `COMPLETED` or `CLOSED` jobs.
  2. Simulate an incoming call or SMS from this client.
- **Pass Criteria:** A `ReengagementEventEntity` is created with status `PENDING`, prompting "Wznów vs Nowe".

### TC-12: SMS Analysis Window Compliance
- **Requirement:** Przestrzega okien analizy SMS (§66.12).
- **Procedure:**
  1. Receive SMS from a number with an active job window -> trigger extraction.
  2. Receive SMS from a number without active job -> verify extraction is skipped.
- **Pass Criteria:** SMS triggers are recorded only within open `JobAnalysisWindow`.

### TC-13: AI Immutability of Approved Data
- **Requirement:** AI nigdy nie nadpisuje istniejących zatwierdzonych danych (§66.13).
- **Procedure:**
  1. In a job with approved address "ul. Główna 5", process an SMS containing "Nowy adres: ul. Polna 10".
  2. Verify the job's address remains "ul. Główna 5" and the new address appears strictly as an unconfirmed `AiSuggestion`.
- **Pass Criteria:** Zero silent overwrites of user-approved data.

### TC-14: Calendar Provider Lifecycle
- **Requirement:** Calendar działa w create/update/delete (§66.14).
- **Procedure:**
  1. Create a job with exact date and time. Confirm calendar sync.
  2. Open system Google Calendar app -> verify event exists.
  3. Change job date in CallUpp -> verify event updates.
  4. Delete job -> verify event is removed from calendar.
- **Pass Criteria:** All three CRUD operations synchronize accurately via `CalendarContract`.

### TC-15: Navigation Launch
- **Requirement:** Nawiguj otwiera Google Maps (§66.15).
- **Procedure:**
  1. Tap "Nawiguj" on a job with client address.
- **Pass Criteria:** Google Maps app opens directly with target location prefilled (`google.navigation:q=...`).

### TC-16: Manual ETA Fallback
- **Requirement:** ETA ma działający manualny fallback (§66.16).
- **Procedure:**
  1. On job detail screen, tap manual arrival time button.
  2. Pick "+30 min" or enter HH:MM.
- **Pass Criteria:** Job displays calculated arrival time without requiring background GPS/location permission.

### TC-17: SMS Template Substitution
- **Requirement:** Szablony SMS działają (§66.17).
- **Procedure:**
  1. Open SMS button on job card, select template containing `{klient}`, `{data}`, `{godzina}`.
- **Pass Criteria:** Default SMS app launches via `ACTION_SENDTO` with variables substituted into template body.

### TC-18: Offline Operation Without AI or Network
- **Requirement:** Podstawowa aplikacja działa bez AI i bez internetu (§66.18).
- **Procedure:**
  1. Enable Airplane Mode (no cellular data, no Wi-Fi).
  2. Perform Call -> Note -> Client -> Job flow.
- **Pass Criteria:** Complete workflow executes 100% locally with zero latency or error dialogs.

### TC-19: Graceful Missing Permission Handling
- **Requirement:** Żaden brak opcjonalnego permission nie powoduje crasha (§66.19).
- **Procedure:**
  1. In system settings, revoke `READ_CALENDAR`, `RECEIVE_SMS`, or `POST_NOTIFICATIONS`.
  2. Launch CallUpp and navigate all screens.
- **Pass Criteria:** App functions cleanly, displaying inline permission banners without throwing unhandled exceptions.

### TC-20: Release APK Verification
- **Requirement:** Build release APK przechodzi wszystkie testy krytyczne (§66.20).
- **Procedure:**
  1. Build release APK: `./gradlew :app:assembleRelease`.
  2. Install on physical device via `adb install -r`.
  3. Verify application launches, renders onboarding on first run, and completes setup.
- **Pass Criteria:** Release APK passes critical runtime scenarios with zero crashes.
