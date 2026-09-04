# Material discoveries and handoffs

Brak uruchomień, buildów i testów. Nie wpisywać fikcyjnych PASS.

Szablon wpisu: data | phase/prompt/CP/PT | base/end commit | wykonana czynność | wynik | dowód w harness/build | materialne odkrycie | konflikt/U | dalsze działanie.

2026-09-04 | INF-01 / INF-01-AUTOMATION-FOUNDATION-r1 / CP-INF-01-automation-foundation-r1 / 7c095129a7f2d2d941fb6d1f29738306b93e3891 | 8fa28f9a31238cc49fc0d342ff55819f3f1f94cb / pending commit | powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 (:app:assembleDebug, :app:testDebugUnitTest, :app:lintDebug) | PARTIAL FAIL (assembleDebug SUCCESS, testDebugUnitTest SUCCESS, lintDebug FAILED) | app/build/reports/lint-results-debug.html | lintDebug error: AndroidManifest.xml:21 PermissionImpliesUnsupportedChromeOsHardware (RECEIVE_SMS missing telephony uses-feature tag). Out-of-scope pre-existing application manifest defect. | brak U; naprawa wymaga modyfikacji AndroidManifest.xml lub app/build.gradle.kts (poza zakresem INF-01) | zachować commity infrastruktury, zwrócić handoff do Control Plane
