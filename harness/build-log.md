# Material discoveries and handoffs

Brak uruchomień, buildów i testów. Nie wpisywać fikcyjnych PASS.

Szablon wpisu: data | phase/prompt/CP/PT | base/end commit | wykonana czynność | wynik | dowód w harness/build | materialne odkrycie | konflikt/U | dalsze działanie.

2026-09-04 | INF-01 / INF-01-AUTOMATION-FOUNDATION-r1 / CP-INF-01-automation-foundation-r1 / 7c095129a7f2d2d941fb6d1f29738306b93e3891 | 8fa28f9a31238cc49fc0d342ff55819f3f1f94cb / e12778675b4e8a3013b7d739b5e5244a76d0ac81 | Local: scripts/verify-local.ps1; CI: GitHub Actions Run 33826812112 on PR #2 | INFRASTRUCTURE PASS / REPO TEST-LINT BLOCKED | GitHub Actions Run 33826812112, app/build/reports/lint-results-debug.html | 1) Local assembleDebug SUCCESS, testDebugUnitTest SUCCESS, lintDebug FAILED on AndroidManifest.xml:21 PermissionImpliesUnsupportedChromeOsHardware (RECEIVE_SMS missing telephony uses-feature). 2) GitHub Actions assembleDebug SUCCESS, testDebugUnitTest FAILED on Robolectric tests (DefaultSdkProvider.java:170 UnsupportedOperationException). Both are pre-existing out-of-scope defects. | brak U; modyfikacje kodu aplikacji, manifestu i zależności Robolectric są poza zakresem INF-01 | Zwrócić handoff do CallUpp AI Control Plane. Nie zmieniać kodu aplikacji w INF-01. Zaplanować fazę naprawczą dla manifestu i testów Robolectric.
