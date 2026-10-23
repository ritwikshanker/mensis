# Contributing to Mensis

Thanks for your interest. A few rules keep the app trustworthy:

1. **Privacy first.** No analytics, no network code outside `:backup-drive`, no health data in logs, no new
   permissions without discussion.
2. **Synthetic data only.** Never commit real cycle data, screenshots of real data, or real account
   details. Test fixtures are generated.
3. **Every dependency is justified.** A pull request that adds a library explains why in its description
   and commit message, names the licence, and confirms it makes no network calls on its own.
4. **Derived, never stored.** Cycles, phases and predictions are computed from daily logs and settings.
   Thresholds live in `PredictionConstants.kt` with a comment a biologist can review.
5. **Copy and layout** follow the app's design spec; keep user-facing wording consistent with existing screens.
6. **Checks must pass**: `./gradlew check` (ktlint, detekt, lint, unit tests) and, for data or UI
   changes, `./gradlew connectedCheck` on a device or emulator.

## Workflow

- Fork, branch from `main`, open a pull request. CI runs ktlint, detekt, Android Lint, unit tests and a
  release build; the emulator workflow runs instrumented tests on `main`.
- One-line commit messages in Conventional Commits form: `feat:`, `fix:`, `chore:`, `docs:`, `build:`,
  `test:`, `refactor:`.
- Report security issues privately via the GitHub security advisory form rather than a public issue.
- Releases are made by the maintainer (`RELEASING.md`); pull requests do not change version numbers or
  store texts unless asked to.

## Building

Requirements: JDK 17 or newer (the Gradle daemon runs on JDK 21, auto-provisioned by the toolchain
resolver) and the Android SDK with platform 37. Then:

```
./gradlew assembleDebug        # debug APK
./gradlew check                # ktlint, detekt, lint, unit tests
./gradlew connectedCheck       # instrumented tests on a connected device or emulator
```

`connectedCheck` installs the app on every connected device and uninstalls it afterwards, which deletes
the app's data there. Set `ANDROID_SERIAL` to one device or emulator when others are attached.

Google Drive backup needs an OAuth web client ID from your own Google Cloud project, set as
`mensis.googleWebClientId` in `~/.gradle/gradle.properties`; [`GOOGLE_CLOUD_SETUP.md`](GOOGLE_CLOUD_SETUP.md)
walks through it. Without it the app builds and works, and the Backup screen says Drive backup is not
configured in this build.

The bundled fonts and Material Symbols icons are not kept in the repository: the first build downloads
them from Google Fonts and google/material-design-icons, together with the Apache License 2.0 text shown
in the app, into `app/build/generated/designAssets` (the `fetchDesignAssets` Gradle task), so the first
build needs network access.

## Store screenshots

The screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/`, also shown in the README,
are drawn from the real screens with the synthetic preview data by the instrumented test
`StoreScreenshots`, which is skipped in normal test runs. To redraw them on one emulator:

```
export ANDROID_SERIAL=emulator-5554
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.deutschdreamers.mensis.ui.StoreScreenshots -e screenshots true \
    com.deutschdreamers.mensis.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.deutschdreamers.mensis/files/screenshots/. \
    fastlane/metadata/android/en-US/images/phoneScreenshots/
```

They are 1080 × 1920 PNGs without an alpha channel, as Google Play requires.

## Project layout

| Module | Purpose |
|---|---|
| `:domain` | Pure Kotlin: models, cycle derivation, prediction engine, insights. No Android imports. |
| `:data` | Room + SQLCipher, key management, repositories, export/import. |
| `:backup-drive` | Google sign-in, Drive client, backup scheduling and restore. The only module with network code. |
| `:app` | Jetpack Compose UI, Navigation 3, theme, notifications, workers. |

The export and import formats are documented in [`docs/export-format.md`](docs/export-format.md), with a
JSON Schema in [`docs/mensis-export.schema.json`](docs/mensis-export.schema.json). Release bundles are built
and signed locally and uploaded to Google Play: [`RELEASING.md`](RELEASING.md) has the checklist,
[`PLAY_CONSOLE.md`](PLAY_CONSOLE.md) the Play Console declarations, and the store texts and graphics live in
`fastlane/metadata/android/en-US/`.
