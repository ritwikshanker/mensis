# Releasing Mensis

Mensis is published on Google Play only. Release bundles are built and signed on the maintainer's
computer in Android Studio; the signing key never goes to GitHub, neither into the repository nor into
GitHub Actions.

## Signing

- The bundle is signed with the maintainer's existing upload keystore (`keystore.jks`, the same one used
  for their other apps). It stays on the computer and in the maintainer's own backup.
  `.gitignore` blocks `*.jks`, `*.keystore`, `*.p12` and `*.pem` as a safety net.
- Google Play App Signing: on the first upload, keep Play's default. Google then creates and keeps the
  key that signs what users install, and `keystore.jks` is only the upload key. If the keystore is ever
  lost or leaked, Play Console can reset the upload key (Test and release → App integrity → App signing).
- After the first upload, copy the **app signing key certificate** SHA-1 from that page into the
  Android OAuth client for release builds (GOOGLE_CLOUD_SETUP.md). Builds installed from Play carry
  Google's certificate, so Drive sign-in works only once that client exists.

## Web client ID

The bundle must contain the Google Cloud web client ID, or Drive backup is "not configured in this
build". It is read from `~/.gradle/gradle.properties` on the building computer:

```
mensis.googleWebClientId=123456789012-abc123def456.apps.googleusercontent.com
```

A signed build without it stops with an error (`backup-drive/build.gradle.kts`).

## Making a release

1. `main` is green: the CI and Instrumented tests workflows passed on the commit to be released. CI
   also fails if the release build asks for a permission that is not on its reviewed list.
2. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example 0.1.1).
   Play rejects an upload whose versionCode is not higher than the last one.
3. Write the release notes as `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: at most
   500 characters (Play's limit), no wording that suggests contraceptive or diagnostic reliability.
   Update the other store texts in `fastlane/metadata/android/en-US/` if features changed.
4. Commit (`chore: release vX.Y.Z`), tag and push:

   ```
   git tag -a vX.Y.Z -m "Mensis X.Y.Z"
   git push origin main vX.Y.Z
   ```

5. Android Studio → Build → Generate Signed App Bundle or APK → Android App Bundle → module
   `Mensis.app`, `keystore.jks` and its alias → variant `release`. The bundle lands in
   `app/release/app-release.aab`.
6. Play Console → the internal testing track → create a release, upload the bundle, paste the
   changelog. Install it from Play on the phone: the update keeps the data, About shows the new version,
   Settings → Backup can connect to Google. Read the pre-launch report, then promote the release.

## GitHub Pages

The privacy policy is `docs/privacy.md`, served at <https://ritwikshanker.github.io/mensis/privacy/>.
Pages deploys from the `main` branch, folder `/docs` (repository Settings → Pages). Play Console's
privacy policy field and the Google Cloud consent screen use that URL; the app opens it from About →
Privacy policy.
