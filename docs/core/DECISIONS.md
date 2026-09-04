# DECISIONS

ENV-2. Rejestr pochodzenia decyzji, bez rozstrzygnięć aplikacyjnych zewnętrznego opiekuna.

## SRC-SP

- TOPIC: Podstawa specyfikacji
- STATUS: ACTIVE
- DECISION: Użytkownik wskazał pełną SP jako najnowszą fundamentalną podstawę. Zachowano dokument bez zmian w MASTER_SPEC.
- SUPERSEDES: wcześniejsze parafrazy opiekuna jako źródło obowiązujących reguł
- SUPERSEDED_BY: —
- SOURCE: załącznik SP i jawne potwierdzenie użytkownika w bieżącym zadaniu
- CONFIDENCE: potwierdzone pochodzenie dokumentu; bez oceny wykonalności lub jakości aplikacji

## SRC-SW

- TOPIC: Ustalenia sposobu produkcji
- STATUS: ACTIVE
- DECISION: Użytkownik wskazał pełny SW jako najnowsze fundamentalne ustalenia. Dokument przechowywany bez zmian; kolejność wypowiedzi pozostaje widoczna.
- SUPERSEDES: zewnętrzne interpretacje procesu
- SUPERSEDED_BY: —
- SOURCE: załącznik SW i jawne potwierdzenie użytkownika
- CONFIDENCE: potwierdzone pochodzenie; nie jest nowym planem wykonania

## Rejestr przyszłych decyzji wewnątrz projektu

Brak nowych decyzji aplikacyjnych podjętych w ramach korekty środowiska.

| ID | TOPIC | STATUS | DECISION | SUPERSEDES | SUPERSEDED_BY | SOURCE | CONFIDENCE |
|---|---|---|---|---|---|---|---|
| D-PROC-001 | Automation execution model | ACTIVE | CallUpp uses the closed ChatGPT project as the control plane, Antigravity as the primary bounded executor, GitHub Actions as the deterministic CI gate, and additional agents such as Jules only selectively. Executors operate one approved phase at a time and do not autonomously begin the next phase. | none | — | explicit user approval in the CallUpp project on 2026-09-04 | explicit user decision |
| D-PROC-002 | Overnight autonomous execution | ACTIVE | CallUpp overnight automation may execute only a finite, explicitly prepared night pack of prompts that were individually approved by the closed ChatGPT CallUpp Control Plane. Every night task must be: READY, reviewed PASS, explicitly night-compatible, independent from the other unmerged tasks in the pack, pinned to an exact base commit. Execution is sequential in isolated Git worktrees. The executor may edit/test/commit/push/open PRs and observe CI only within each supplied prompt. It must never merge, choose a new phase, reinterpret product requirements, or create additional unlisted work. Morning semantic acceptance remains with the CallUpp Control Plane and user. | none | — | explicit user approval, 2026-09-04 | explicit user decision |

Wypełnia projekt na podstawie rzeczywistych decyzji i ich lokalizatorów. Dopuszczalne statusy ACTIVE / SUPERSEDED / REJECTED. Nie traktować dawnego oznaczenia ACTIVE nadanego przez zewnętrznego opiekuna jako dodatkowej akceptacji użytkownika. Wycofany rejestr jest zachowany w archiwum, bez prawa wykonania.

## D-PROC-002

- TOPIC: Overnight autonomous execution
- STATUS: ACTIVE
- DECISION: CallUpp overnight automation may execute only a finite, explicitly prepared night pack of prompts that were individually approved by the closed ChatGPT CallUpp Control Plane. Every night task must be: READY, reviewed PASS, explicitly night-compatible, independent from the other unmerged tasks in the pack, pinned to an exact base commit. Execution is sequential in isolated Git worktrees. The executor may edit/test/commit/push/open PRs and observe CI only within each supplied prompt. It must never merge, choose a new phase, reinterpret product requirements, or create additional unlisted work. Morning semantic acceptance remains with the CallUpp Control Plane and user.
- SUPERSEDES: none
- SUPERSEDED_BY: —
- SOURCE: explicit user approval, 2026-09-04
- CONFIDENCE: explicit user approval; no product decision

