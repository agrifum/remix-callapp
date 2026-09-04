# IMPLEMENTATION_PROMPT_TEMPLATE — Android Studio

STATUS: TEMPLATE / NON-EXECUTABLE. To nie jest zlecenie implementacji bieżącego projektu.

## PROMPT ID

<IMP-XX-revN; PHASE-ID; CP-ID; PT-version; ledger entry>

## ROLE

<Implementation Agent in Android Studio>

## SOURCE FILES TO READ

<AGENTS.md first; exact CP path and only relevant canonical excerpts>

## OBJECTIVE

<one observable outcome>

## CURRENT STATE

<repository/branch/commit, existing components, known limits; do not assume>

## IN SCOPE

<small bounded phase>

## EXPLICIT NON-GOALS

<specific excluded actions and future phases>

## RELEVANT PRODUCT REQUIREMENTS

<active R-ID, linked D-ID, precise relevant behavior, provenance>

## RELEVANT TECHNICAL EVIDENCE

<T-ID, official URL, checked-at date, matching API/version/channel; documented vs inference>

## ASSUMPTIONS

<technical assumptions only, with impact; no product assumptions>

## UNRESOLVED ISSUES

<relevant U-ID; if blocking: BLOCKED, no execution>

## FILES / COMPONENTS ALLOWED TO CHANGE

<explicit path allowlist; no changes outside it>

## DEPENDENCY POLICY

<reuse current compatible dependencies; new dependency needs documented reason and authoritative source; verify stable compatibility>

## IMPLEMENTATION CONSTRAINTS

<requirements, permissions, boundaries, preservation of unrelated changes>

## EXPECTED OUTPUT

<bounded diff and concise evidence report>

## ACCEPTANCE CRITERIA

<observable conditions tied to R-ID; no invented product requirement>

## STOP CONDITIONS

<complete acceptance OR blocked by context/conflict/out-of-scope permission; no next phase>

## HANDOFF REQUIREMENTS

<prompt/CP/PT/commit IDs, changes, evidence, discoveries, uncertainties, next authorized step>


## Operating instructions

Read AGENTS.md first. Read only the listed context files; if a required file is unavailable, report the missing context instead of guessing. Use the current repository state as technical truth about existing code, not as authority to change product requirements. Use current Android documentation when API behavior is uncertain.

Implement only the requested phase. Do not redesign unrelated components, implement future phases, or silently change requirements. Do not add dependencies without a documented reason and compatibility evidence. Record material discoveries with file/source locators. Stop when the defined phase is complete or blocked and produce the required handoff.

Test/build actions belong only to a later explicitly scoped execution phase; do not infer authorization to configure devices or IDE from this template.
