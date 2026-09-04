# Automation & Continuous Integration

This document describes the deterministic repository verification infrastructure established in **Phase INF-01 — Automation Foundation**.

---

## Local Verification

Cross-platform local verification scripts execute the canonical verification pipeline:
1. `:app:assembleDebug` — compiles code and generates debug APK.
2. `:app:testDebugUnitTest` — executes local unit tests.
3. `:app:lintDebug` — runs Android Lint static analysis.

### Windows (PowerShell)
```powershell
powershell -ExecutionPolicy Bypass -File .\scriptserify-local.ps1
```

### Linux / macOS / WSL (Bash)
```bash
./scripts/verify-local.sh
```

---

## Disposable Debug Keystore

- The Android debug signing configuration in `app/build.gradle.kts` expects `${rootDir}/debug.keystore`.
- `debug.keystore` is ignored by Git in `.gitignore` and must **never** be committed.
- Both `scripts/verify-local.sh` and `scripts/verify-local.ps1` check for the existence of `debug.keystore` and automatically generate a standard disposable keystore using `keytool` if absent.
- This is for debug builds and automated testing only; it is not release signing material.

---

## GitHub Actions CI

- **Workflow:** `.github/workflows/android-ci.yml`
- **Triggers:** Push to `main`, Pull Requests targeting `main`.
- **Permissions:** Least-privilege `contents: read`.
- **Concurrency:** Automatically cancels redundant/stale in-progress workflow runs on the same ref (`cancel-in-progress: true`).
- **Runner Environment:** `ubuntu-latest`, JDK 17 (Temurin).
- **Gradle & Wrapper:** Gradle Wrapper 9.3.1 managed via `gradle/actions/setup-gradle@v4`.
- **Caching:** Gradle User Home and Wrapper distribution caching are managed automatically by `setup-gradle@v4`. Secondary caches are avoided to prevent contention.
- **Wrapper Validation:** Automatically performed by `setup-gradle@v4`.
- **Validation Steps:**
  1. Whitespace and diff validation via `git diff --check`.
  2. Execution of `./scripts/verify-local.sh`.

---

## Boundaries & Non-Goals in Current Phase

- **No Device / Emulator / Firebase / Maestro Testing:** Automated physical device and instrumentation testing are not configured in this phase.
- **No Release Signing:** Release signing configurations, credentials, and artifact uploads are not implemented in this phase.
- **No Branch Protection Settings:** GitHub repository branch protection rulesets are repository settings outside the repository codebase and are not managed by INF-01.
