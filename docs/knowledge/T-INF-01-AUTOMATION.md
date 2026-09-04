# T-INF-01-AUTOMATION

Evidence and technical baseline for Phase INF-01 — Automation Foundation.

---

## DOCUMENTED FACTS

1. **Workflow-level concurrency cancellation (T-INF-01-01):**
   GitHub Actions supports workflow-level concurrency cancellation using:
   ```yaml
   concurrency:
     group: ${{ github.workflow }}-${{ github.ref }}
     cancel-in-progress: true
   ```
   This automatically cancels redundant or stale runs of the same workflow on the same ref when new commits are pushed.

2. **Least-privilege permissions (T-INF-01-02):**
   A build-only verification workflow requires only repository read access:
   ```yaml
   permissions:
     contents: read
   ```
   No elevated write or administrative permissions are needed.

3. **Gradle User Home & Wrapper caching (T-INF-01-03):**
   The `gradle/actions/setup-gradle` action provides integrated caching of Gradle User Home and wrapper distributions out of the box. Secondary caching actions (e.g. `actions/cache` or `actions/setup-java` caching) should not be combined with it to avoid cache contention.

4. **Automatic Wrapper validation (T-INF-01-04):**
   Starting with `gradle/actions/setup-gradle@v4`, wrapper checksum validation is performed automatically during setup. Adding a separate wrapper-validation action is redundant.

---

## PROJECT DECISION

- **T-INF-01-05:** Keep the existing major action versions (`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`) in this phase. This minimizes tool-version churn during the initial foundation phase. Major version migrations belong in explicitly scoped future maintenance.

---

## UNKNOWN / OUT OF SCOPE

- **Repository visibility & Branch protection:** GitHub repository visibility and branch protection / ruleset settings are administrative settings outside repository infrastructure and are not modified by INF-01.
- **Product behavior:** INF-01 implements no product requirements; all product behavior decisions remain defined exclusively in `docs/core/MASTER_SPEC.md`.
