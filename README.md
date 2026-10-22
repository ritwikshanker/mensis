# Mensis

A private menstrual cycle tracker for Android. Your data stays on your phone, encrypted. No account,
no analytics, no ads. Optional encrypted backup to your own Google Drive.

**Status: under construction.** Nothing is released yet.

## Privacy model

- All health data lives in a SQLCipher-encrypted database. The database key is 32 random bytes wrapped
  by an AES-256-GCM key in the Android Keystore (StrongBox when the device has it), so the database is
  readable only on this device.
- The app never contacts a server unless you turn on Google Drive backup. Backups are encrypted on the
  phone with a key derived from a password only you know (Argon2id, AES-256-GCM) and stored in Drive's
  hidden app-data folder. Google sees ciphertext and a file size.
- No analytics, crash reporting, ads or tracking SDKs. The only Google libraries are the ones needed for
  sign-in and Drive. The app works fully without Google Play Services; only Drive backup is unavailable.
- Android's own backup and device-to-device transfer are disabled. Screenshots and the Recents thumbnail
  are blocked by default. Notifications say only "Reminder" unless you choose otherwise.
- Predictions are statistics about your own history. Mensis is not a medical device and is not a method
  of contraception.

The threat model is described in [`PRIVACY.md`](PRIVACY.md). The export and import formats
are documented in [`docs/export-format.md`](docs/export-format.md), with a JSON Schema in
[`docs/mensis-export.schema.json`](docs/mensis-export.schema.json).

## Building

Requirements: JDK 17 or newer (the Gradle daemon runs on JDK 21, auto-provisioned by the toolchain resolver), Android SDK with
platform 37. Then:

```
./gradlew assembleDebug        # debug APK
./gradlew check                # ktlint, detekt, lint, unit tests
./gradlew connectedCheck       # instrumented tests on a connected device or emulator
```

Google Drive backup needs an OAuth web client ID from your own Google Cloud project, set as
`mensis.googleWebClientId` in `~/.gradle/gradle.properties`; [`GOOGLE_CLOUD_SETUP.md`](GOOGLE_CLOUD_SETUP.md)
walks through it. Without it the app builds and works, and the Backup screen says Drive backup is not
configured in this build.

The bundled fonts and Material Symbols icons are not kept in the repository: the first build downloads
them from Google Fonts and google/material-design-icons, together with the Apache License 2.0 text shown
in the app, into `app/build/generated/designAssets` (the `fetchDesignAssets` Gradle task), so the first
build needs network access.

## Project layout

| Module | Purpose |
|---|---|
| `:domain` | Pure Kotlin: models, cycle derivation, prediction engine, insights. No Android imports. |
| `:data` | Room + SQLCipher, key management, repositories, export/import. |
| `:backup-drive` | Google sign-in, Drive client, backup scheduling and restore. The only module with network code. |
| `:app` | Jetpack Compose UI, Navigation 3, theme, notifications, workers. |

## Licence

MIT, see `LICENSE`. Third-party notices (fonts, icons, libraries) are in `THIRD_PARTY_NOTICES.md`.
