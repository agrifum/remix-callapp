# CallUpp Night Runner & Pack Architecture

This directory defines the formal contract and separation of concerns for unattended overnight execution in CallUpp.

---

## Separation of Concerns

1. **CANONICAL SOURCES:**
   - Repository control files (`docs/core/MASTER_SPEC.md`, `docs/core/DECISIONS.md`, `AGENTS.md`, `docs/control/*`).
   - Repository state and canonical history define product behavior and requirements.
   - Runtime packs never override or alter canonical specifications.

2. **PROMPT CONTROL:**
   - `docs/prompts/PROMPT_LEDGER.md`, `docs/prompts/PROMPT_REVIEW.md`, `harness/context/CP-*.md`, and the authoritative prompt contracts.
   - Every task included in a night pack must already have `STATUS: EXECUTED` or `READY`, a recorded review of `PASS`, explicit authorization, and declared independence.

3. **RUNTIME NIGHT PACK:**
   - `%LOCALAPPDATA%\CallUpp\night-runner\packs\<pack-id>\`
   - Contains the runtime `queue.json` manifest conforming to `queue.schema.json`, alongside relative prompt Markdown files.
   - These are transient execution artifacts, stored outside Git.

4. **RUNTIME LOGS:**
   - `%LOCALAPPDATA%\CallUpp\night-runner\runs\<pack-id>\`
   - Contains raw stdout/stderr logs for each task run, step telemetry, and the final `summary.json`.

5. **WORKTREES:**
   - `%LOCALAPPDATA%\CallUpp\night-runner\worktrees\<task-id>\`
   - Isolated Git worktrees created directly from the pinned `base_main_sha`.
   - Blocked tasks retain their worktrees for manual inspection; completed tasks are cleaned up safely.

---

## Authority & Non-Goals

- **No Autonomous Task Selection:** The Night Runner is an executor, not a planner. It never selects, invents, or schedules future phases.
- **Control Plane Authority:** Only the closed ChatGPT CallUpp Control Plane prepares and approves night pack manifests.
- **No Production Pack in INF-01B:** No active night pack or scheduled task is created in this phase.
