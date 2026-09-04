# CallUpp repository agent rules

This is the implementation repository for CallUpp.

Canonical product behavior lives in docs/core/MASTER_SPEC.md.
Do not reproduce or reinterpret the specification in this file.

Before changing application code:
read this AGENTS.md first;
read the single phase context pack explicitly named by the current task;
read only canonical sections referenced by that pack.

Source hierarchy:

1. Current explicit user decisions after coherent canonical update.
2. docs/core/MASTER_SPEC.md for product behavior.
3. docs/core/DECISIONS.md for recorded later decisions and provenance.
4. docs/core/UNRESOLVED.md for genuinely unresolved product questions.
5. Current repository state for what code actually exists.
6. Current authoritative Android/Google documentation for API mechanics and platform behavior.

Rules:

Never invent missing product decisions.
Never silently change WHAT because of a technical finding about HOW.
Never redesign unrelated components.
Never add unrequested functionality.
Work on one explicitly approved phase only.
Respect the current task's allowed-file boundary.
Preserve unrelated changes.
Do not add or upgrade dependencies without explicit scope and authoritative justification.
Do not treat summaries, indexes, historical chat, old prompts, or archived analysis as stronger than canonical source files.
Do not read the full historical conversation unless explicitly required for provenance.
If required context is missing, stale, contradictory, or outside scope, stop instead of guessing.
After execution return a concise handoff containing base commit, end commit, changed files, actual verification performed, material discoveries, unresolved issues, and the next permitted step.
Never automatically start the next phase.

AI Studio execution note:
When Google AI Studio Build is used as executor, the implementation prompt must explicitly tell the agent to read AGENTS.md and the named phase context pack before editing. Do not assume AI Studio automatically loads repository rule files as system instructions.
