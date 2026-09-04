# Phase Context: INF-01 — Automation Foundation

- **CP ID:** CP-INF-01-automation-foundation-r1
- **Phase:** INF-01
- **Prompt ID:** INF-01-AUTOMATION-FOUNDATION-r1
- **Review:** PRV-INF-01-r1 PASS
- **Date:** 2026-09-04
- **PT Version:** docs/prompts/PROMPT_TEMPLATE.md blob 7c095129a7f2d2d941fb6d1f29738306b93e3891
- **Base Commit:** 8fa28f9a31238cc49fc0d342ff55819f3f1f94cb
- **Execution Target:** Antigravity AI Pro

---

## 1. Objective

Establish the first deterministic automation foundation for CallUpp:
- Strengthen the existing Android GitHub Actions workflow (`.github/workflows/android-ci.yml`).
- Provide cross-platform local verification scripts (`scripts/verify-local.sh`, `scripts/verify-local.ps1`).
- Provide a reusable Pull Request template (`.github/pull_request_template.md`).
- Record phase-specific context, evidence, prompt ledger, review, and build log state.
- Validate automation deterministically.

---

## 2. Relevant Control Sources

- `AGENTS.md`
- `docs/control/ENVIRONMENT_BOUNDARY.md`
- `docs/control/HANDOFF_PROTOCOL.md`
- `docs/control/CHANGE_PROTOCOL.md`
- `docs/prompts/PROMPT_CONTRACT.md`
- `docs/prompts/PROMPT_TEMPLATE.md`
- `docs/prompts/REVIEW_PROMPT_TEMPLATE.md`
- `docs/prompts/PROMPT_LEDGER.md`
- `docs/prompts/PROMPT_REVIEW.md`
- `docs/knowledge/TECHNICAL_SOURCE_POLICY.md`
- `harness/context/README.md`
- `harness/build-log.md`

Canonical product specification pointer: `docs/core/MASTER_SPEC.md` (no product feature is implemented in this phase).

---

## 3. Current Verified CI State

- Base commit: `8fa28f9a31238cc49fc0d342ff55819f3f1f94cb`
- Existing workflow: `.github/workflows/android-ci.yml` (runs on push/pull_request to main, uses ubuntu-latest, JDK 17, setup-gradle@v4, previously ran only `:app:compileDebugKotlin`).
- Gradle wrapper: 9.3.1
- Java baseline: 17
- Signing configuration: debug configuration expects `${rootDir}/debug.keystore`, ignored by Git in `.gitignore`.

---

## 4. Technical Evidence

- **T-INF-01-01 (Documented):** GitHub Actions supports workflow-level concurrency cancellation (`group: ${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true`).
- **T-INF-01-02 (Documented):** Build-only GitHub Actions workflow uses least-privilege `permissions: contents: read`.
- **T-INF-01-03 (Documented):** `gradle/actions/setup-gradle` provides caching for Gradle User Home and Wrapper distributions.
- **T-INF-01-04 (Documented):** `setup-gradle` v4 performs wrapper validation automatically; no separate wrapper-validation action should be added.
- **T-INF-01-05 (Project Decision):** Keep existing action major versions (`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`) to avoid tool-version churn.

---

## 5. Explicit Non-Goals

- Do NOT modify application source code under `app/src/**`.
- Do NOT modify `AndroidManifest.xml` or Android resources.
- Do NOT modify `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, or `gradle/libs.versions.toml`.
- Do NOT change Gradle version or add/upgrade dependencies.
- Do NOT add device/emulator tests, Firebase Test Lab, Maestro, Jules, release signing, or secrets.
- Do NOT change repository visibility or branch protection settings.
- Do NOT merge the PR or begin INF-02.

---

## 6. Allowed File Allowlist

Only these paths may be created or modified:
- `.github/workflows/android-ci.yml`
- `.github/pull_request_template.md`
- `scripts/verify-local.sh`
- `scripts/verify-local.ps1`
- `docs/automation/README.md`
- `docs/knowledge/T-INF-01-AUTOMATION.md`
- `docs/core/DECISIONS.md`
- `docs/prompts/PROMPT_LEDGER.md`
- `docs/prompts/PROMPT_REVIEW.md`
- `harness/context/CP-INF-01-automation-foundation.md`
- `harness/build-log.md`
*(Disposable `debug.keystore` is ignored and must never be committed)*

---

## 7. Acceptance Criteria

1. Work starts from exact base `8fa28f9a31238cc49fc0d342ff55819f3f1f94cb`.
2. No application behavior changed.
3. No tracked file outside allowlist changed.
4. Existing Android CI strengthened rather than duplicated.
5. CI uses JDK 17, Gradle Wrapper, setup-gradle@v4, `contents: read`, concurrency cancellation.
6. CI executes `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`.
7. Local Bash and PowerShell scripts execute the same three Gradle checks.
8. Missing `debug.keystore` created only as ignored disposable debug material.
9. No release keys or secrets added.
10. `git diff --check` passes cleanly.
11. Prompt/CP/review/decision/evidence records coherent.
12. Branch contains clean commits.
13. Branch pushed and PR opened if credentials permit.
14. GitHub Actions observed if PR is created.
15. PR is not merged autonomously.

---

## 8. Stop Conditions

STOP immediately if:
- Base commit differs from `8fa28f9a31238cc49fc0d342ff55819f3f1f94cb`.
- Required files are missing or instructions conflict.
- Changes require any file outside allowlist.
- Build/lint failures require application source code or dependency changes.
- Safe push credentials unavailable (preserve commits and report BLOCKED).
- Acceptance criteria are fulfilled. Never begin INF-02.
