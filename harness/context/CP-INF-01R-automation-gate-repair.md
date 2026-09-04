# Context Pack: CP-INF-01R-automation-gate-repair-r1

## Metadata
- **CP:** CP-INF-01R-automation-gate-repair-r1
- **Prompt:** INF-01R-AUTOMATION-GATE-REPAIR-r1
- **Review:** PRV-INF-01R-r1 PASS
- **Parent phase:** INF-01
- **Repair base:** 24034bf0441fca6788093cab0eed01d274421728
- **main base:** 8fa28f9a31238cc49fc0d342ff55819f3f1f94cb
- **Scope:** CI gate repair only.

## Scope and Intent
Bounded repair of PR #2 automation gate:
1. Update GitHub Actions runner Java runtime from JDK 17 to JDK 21 to support Robolectric 4.16.1 execution on Android API 36.
2. Add `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to `app/src/main/AndroidManifest.xml` to resolve Android Lint `PermissionImpliesUnsupportedChromeOsHardware`.
3. Fix formatting / control character in `docs/automation/README.md`.
4. Register prompt ledger, review, technical evidence, build log, and handoff metadata.
