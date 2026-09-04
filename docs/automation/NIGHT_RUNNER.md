# CallUpp Night Runner

The CallUpp Night Runner provides deterministic, auditable, and isolated unattended execution for pre-approved CallUpp phase prompts during overnight runs.

---

## Architecture

The end-to-end execution flow is structured as follows:

```
Closed ChatGPT CallUpp Control Plane
  │  (Designs, reviews, and approves independent tasks)
  ▼
Approved Night Pack Manifest (queue.json)
  │  (Stored in %LOCALAPPDATA%\CallUpp\night-runner\packs\<pack-id>\)
  ▼
One-Time Antigravity Scheduled Task (/schedule)
  │  (Triggers overnight run at designated time)
  ▼
Antigravity Workspace Skill (callupp-night-run)
  │  (Invoked with explicit QueuePath parameter)
  ▼
Read-Only Preflight (scripts/night-preflight.ps1)
  │  (Validates AC power, sleep/hibernate timeouts, disk, git, gh, agy canary)
  ▼
Night Runner Orchestrator (scripts/night-runner.ps1)
  │  (Validates queue schema, matches origin/main base SHA)
  ▼
Sequential Task Execution in Isolated Git Worktrees
  │  (For each task: creates worktree, copies transient .callupp-night-task.md)
  ▼
Fresh Headless Antigravity Agent (agy -p)
  │  (Performs task edits, tests, commits, pushes branch, opens PR)
  ▼
GitHub Actions CI Gate (gh pr checks --watch)
  │  (Verifies assembleDebug, testDebugUnitTest, lintDebug on PR)
  ▼
Cleanup & Evidence Recording
  │  (Cleans completed worktrees; retains blocked worktrees for inspection)
  ▼
Morning Control Plane Review
  │  (Manual inspection of candidates and review of PRs)
  ▼
Manual Merge Decision
```

---

## Strict Core Guardrails

- **NO AUTO MERGE:** The runner never merges pull requests. Merging into `main` requires explicit human review and approval.
- **NO AUTO NEXT PHASE:** The runner executes only the finite, explicitly approved tasks in the supplied manifest. It never chooses or initiates subsequent phases.
- **NO PRODUCT DECISIONS:** Product requirements are strictly determined by `docs/core/MASTER_SPEC.md` through the closed ChatGPT CallUpp Control Plane.
- **AGENT SKILL OVER LEGACY WORKFLOW:** The runner uses a modern Antigravity Agent Skill (`.agents/skills/callupp-night-run/SKILL.md`). Legacy Antigravity Workflows are deprecated and will be retired on 2026-11-01.
- **NO UNRESTRICTED PERMISSIONS:** `--dangerously-skip-permissions` is strictly forbidden. All actions follow Antigravity scoped permission guardrails.

---

## Host & Power Prerequisites

For safe unattended overnight execution, the host computer must meet the following verified conditions:

1. **AC Power:** The laptop must be continuously connected to AC mains power. Running on battery power will cause the preflight to fail closed (`BLOCKED`).
2. **AC Sleep Timeout:** Set to `0` (Never).
3. **AC Hibernation Timeout:** Set to `0` (Never).
4. **Laptop Lid Behavior:** If lid close is configured to Sleep, the laptop lid **must remain open** throughout the night run.
5. **Display Timeout:** May turn off normally (e.g. 5 minutes); turning off the display does not interrupt background tasks.
6. **Disk Space:** Both the repository drive and runtime data drive (`C:`) must have at least 10 GB of free space.
7. **Pending Restarts:** No pending Windows Update or Component Based Servicing reboot markers.

---

## Runtime Storage Layout

Runtime execution files are kept outside the repository to prevent Git index pollution:

- **Night Packs:** `%LOCALAPPDATA%\CallUpp\night-runner\packs\<pack-id>\`
- **Runtime Logs & Summaries:** `%LOCALAPPDATA%\CallUpp\night-runner\runs\<pack-id>\`
  - `summary.json`: High-level machine-readable record of overall and per-task results.
  - `<task-id>\stdout.log`: Raw agent output.
  - `<task-id>\stderr.log`: Raw agent errors.
- **Isolated Worktrees:** `%LOCALAPPDATA%\CallUpp\night-runner\worktrees\<task-id>\`
  - Successfully verified tasks are automatically cleaned up.
  - Blocked tasks retain their worktrees for forensic audit and debugging.

---

## Future Invocation Model

When a real night pack is prepared by the CallUpp Control Plane:
1. The queue manifest and prompt files are placed in `%LOCALAPPDATA%\CallUpp\night-runner\packs\<pack-id>\`.
2. A one-time Antigravity Scheduled Task is scheduled:
   ```text
   /schedule prompt="Use /callupp-night-run with QueuePath='C:\Users\...\AppData\Local\CallUpp\night-runner\packs\<pack-id>\queue.json'"
   ```
3. In the morning, the user reviews `%LOCALAPPDATA%\CallUpp\night-runner\runs\<pack-id>\summary.json` and the corresponding pull requests.
