# T-INF-01B-NIGHT-AUTOMATION

Technical baseline and evidence for Phase INF-01B — Night Runner Foundation.
Checked against official documentation and local environment on 2026-09-04.

---

## DOCUMENTED FACT

1. **T-INF-01B-01 — DOCUMENTED:**
   Antigravity Scheduled Tasks can start agent prompts in the background at a future time or on a cron schedule. `/schedule` is the public scheduling command.

2. **T-INF-01B-02 — DOCUMENTED:**
   Antigravity Projects natively support isolated Git worktrees and project-scoped settings/permissions.

3. **T-INF-01B-03 — DOCUMENTED:**
   Workspace Agent Skills live under `.agents/skills/<skill-name>/SKILL.md` and are reusable via slash-command invocation.

4. **T-INF-01B-04 — DOCUMENTED:**
   Legacy Antigravity Workflows are deprecated and scheduled for retirement on 2026-11-01. New reusable automation in this phase must therefore use an Agent Skill, not a new Workflow.

5. **T-INF-01B-05 — DOCUMENTED:**
   Antigravity CLI headless mode supports `agy -p "<prompt>"` with machine-readable JSON output and configurable print timeout. It uses cached authentication and exits non-zero for genuine execution errors.

6. **T-INF-01B-06 — DOCUMENTED:**
   In headless mode, actions that require interactive approval cannot be approved interactively. Fine-grained permissions must be granted in advance. Unconfigured command actions default to Ask/soft-denial. The Night Runner must never use `--dangerously-skip-permissions`.

7. **T-INF-01B-07 — DOCUMENTED:**
   Fine-grained Antigravity permissions use Allow / Ask / Deny semantics, with Deny and Ask taking precedence over Allow. Prefer explicit scoped command permissions over broad always-proceed or unrestricted machine access.

8. **T-INF-01B-08 — DOCUMENTED:**
   GitHub CLI supports `gh pr checks <pr-or-branch> --watch` and returns a non-zero status for failing checks.

---

## INFERENCE

1. Sequential execution in isolated Git worktrees completely isolates git index, untracked files, and build artifacts between tasks.
2. Relying on cached GCM authentication allows `gh` to perform read-only check monitoring (`gh pr checks`) without manual interactive login prompts during night runs.
3. Placing runner prompt metadata into a transient `.callupp-night-task.md` file avoids command-line length limits and escape character corruption on Windows PowerShell.

---

## PROJECT DECISION

1. **Agent Skill over legacy Workflow:** Use an Agent Skill (`.agents/skills/callupp-night-run/SKILL.md`) rather than a legacy Antigravity Workflow because the Workflow mechanism is deprecated.
2. **Strict permission safety:** Do not use `--dangerously-skip-permissions`.
3. **Sequential execution:** Execute tasks sequentially, not concurrently, for the first version of the Night Runner to avoid resource contention and eliminate concurrent build races.
4. **Finite one-time scheduling:** Use a one-time scheduled invocation for each real night pack rather than an unbounded recurring schedule. Real packs must be explicitly approved and bounded.

---

## UNKNOWN / RUNTIME CHECK

1. **Antigravity headless CLI & permissions:** Whether this machine's installed Antigravity CLI and current permission configuration are sufficient for fully headless tool execution.
   - *Runtime verification (2026-09-04):* `agy version 1.1.22` verified; canary test returned `CALLUPP_HEADLESS_OK` with `status: SUCCESS`. Headless execution of `git rev-parse HEAD` verified with `status: SUCCESS` without interactive prompts.
