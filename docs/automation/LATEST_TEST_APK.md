# Latest CallUpp Test APK

The `Build Latest CallUpp Test APK` workflow builds the current `main` branch after running the repository verification script (`assembleDebug`, unit tests, and lint).

The workflow restores a dedicated test-only `debug.keystore` from GitHub Actions cache using the fixed cache key `callupp-test-debug-keystore-v1`. If the cache is missing on the first run, it creates the key and GitHub Actions stores it at job completion. This keeps routine test APKs signed consistently without committing signing material to the repository.

The downloadable artifact contains:

- `CallUpp-latest-test.apk`
- `CallUpp-latest-test.apk.sha256`

The test signing key is not a release key. If GitHub Actions cache is ever evicted or reset, a newly generated key will require a one-time reinstall of the test application before future in-place test updates work again.
