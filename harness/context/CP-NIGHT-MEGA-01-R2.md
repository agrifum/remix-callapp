# Context Pack: CP-NIGHT-MEGA-01-R2

## Metadata
- **Pack ID:** callupp-mega-20260904-acf2cf1f
- **Exact Base SHA:** acf2cf1f88bc9f6db8ca52c4e4619b16634890f7
- **Previous Blocked Pack ID:** callupp-mega-20260904-a2656318
- **Reason for Reauthorization:** INF-01B-R2 Night Runner infrastructure repair (PR #4 merged to main at acf2cf1f88bc9f6db8ca52c4e4619b16634890f7, resolving branch collision check null-handling defect).
- **Execution Target:** Antigravity AI Pro / CallUpp Night Runner
- **Night-Compatible:** true
- **Independent:** true
- **Auto-Merge Policy:** false (NO AUTO MERGE; manual human/Control Plane review required)
- **Auto-Next-Phase Policy:** false (NO AUTO NEXT PHASE; strictly bounded execution)

## Reauthorized Prompts and Reviews

| Order | Phase | Prompt ID | Review ID | Review Status | Branch | Task Role / Objective |
|---|---|---|---|---|---|---|
| 1 | NIGHT-CTRL-01 | NIGHT-CTRL-REGISTER-r2 | PRV-NIGHT-CTRL-r2 | PASS | chore/nightpack-control-20260904 | Durable repository control metadata registration |
| 2 | INF-02 | INF-02-CHARACTERIZATION-r2 | PRV-INF-02-r2 | PASS | test/inf-02-characterization | Characterization and regression safety net tests |
| 3 | AUD-BASE | AUD-BASE-MASTER-SPEC-r2 | PRV-AUD-BASE-r2 | PASS | audit/aud-base-20260904 | Fresh static source compliance baseline audit against SP-001..SP-068 |
| 4 | RSCH-CALENDAR | RSCH-CALENDAR-CONSISTENCY-r2 | PRV-RSCH-CALENDAR-r2 | PASS | research/calendar-consistency-20260904 | Android Calendar Provider consistency and failure modes research |
| 5 | RSCH-TELEPHONY | RSCH-TELEPHONY-OUTGOING-r2 | PRV-RSCH-TELEPHONY-r2 | PASS | research/telephony-outgoing-20260904 | Outgoing active-call and phone identity signal research |
| 6 | AUD-SMS-JOB | AUD-SMS-JOB-LIFECYCLE-r2 | PRV-AUD-SMS-JOB-r2 | PASS | audit/sms-job-lifecycle-20260904 | SMS/AI analysis window and Job status lifecycle deep audit |

## Governance & Canonical Guardrails

1. **Traceability Standards:** TRACEABILITY strictly uses canonical SP-xxx IDs (SP-001 through SP-068). Legacy R-IDs are obsolete and must not be created or referenced.
2. **Unresolved Registry:** Old issues U-008 through U-016 were previously withdrawn by external maintainer and must not be recreated.
3. **Execution Model:** Tasks execute sequentially in isolated Git worktrees rooted at exact base `acf2cf1f88bc9f6db8ca52c4e4619b16634890f7`.
4. **No Product Decisions:** All product behavior remains governed by `docs/core/MASTER_SPEC.md` under the closed ChatGPT CallUpp Control Plane.
5. **Post-Run Hand-off:** Completed task branches open pull requests for GitHub Actions CI verification. Final acceptance and merging remain subject to manual Control Plane review.