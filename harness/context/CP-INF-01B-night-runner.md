# Context Pack: CP-INF-01B-night-runner-r1

## Metadata
- **CP ID:** CP-INF-01B-night-runner-r1
- **Prompt:** INF-01B-NIGHT-RUNNER-r1
- **Review:** PRV-INF-01B-r1 PASS
- **PT:** 7c095129a7f2d2d941fb6d1f29738306b93e3891
- **Base:** 76b2315d38f280792f5fe15c19af643dc9b097c7
- **Execution target:** Antigravity AI Pro
- **Objective:** Night Runner infrastructure only.

## Scope and Intent
Establish a minimal, deterministic, and auditable Night Runner foundation that allows a future one-time overnight pack of already-approved independent tasks to run unattended in isolated Git worktrees.
- No application code, Gradle, or dependency changes.
- Read-only environment preflight (`scripts/night-preflight.ps1`).
- Deterministic runner (`scripts/night-runner.ps1`) with strict JSON queue schema.
- Workspace Agent Skill (`.agents/skills/callupp-night-run/SKILL.md`).
- Self-test capability without remote side-effects.
