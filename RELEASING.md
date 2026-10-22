# Releasing Mensis

Mensis ships in two ways from the same tag: an Android App Bundle for Google Play and an APK on the
GitHub release page. Both should carry the same signature, so that a phone can move between them
without uninstalling (which would delete the data) and so that one Google Cloud OAuth client covers
both (GOOGLE_CLOUD_SETUP.md).

## Keys

| Key | Signs | Where it lives |
|---|---|---|
| **App signing key** | What users install: Google Play re-signs every bundle with it (Play App Signing), and the GitHub APK is signed with it on your computer | Offline: your computer and an offline backup. Google Play holds a copy after enrolment. Never in CI |
| **Upload key** | The bundle uploaded to Play, so that Play knows the upload is yours | GitHub Actions secrets of this repository, and your offline backup |

Losing the app signing key means GitHub APKs can no longer update existing installs. Losing the upload
key is recoverable: Play Console can reset it (Test and release → App integrity → App signing).

### Using a key you already have

A key that already serves other apps can be used here, with these consequences:

- **As the upload key: fine.** It only proves to Play that the upload comes from you.
- **As the app signing key: possible, but a key of its own is better.** Every app signed with the same
  certificate can be granted Mensis's signature-level permissions, and a leak of that one key affects
  all those apps. Mensis's data stays private either way (no shared user ID, nothing exported), but a
  separate key costs nothing.

The recommended setup is therefore: the existing key as the **upload key**, and a **new app signing key
for Mensis**, enrolled in Play App Signing before the first upload.

If you prefer to let Google generate the app signing key instead, Play builds are signed with a key you
can never export. GitHub APKs then have to be signed with a different key (for example your existing
one): they cannot update a Play install or the other way round, and Google Cloud needs a second Android
OAuth client with that key's SHA-1.

### 1. Create the app signing key (once)

```
keytool -genkeypair -v -storetype PKCS12 -keystore mensis-app-signing.p12 -alias mensis \
  -keyalg RSA -keysize 4096 -validity 10000
```

keytool asks for a password and the certificate's name fields. Keep the file **outside the repository**
(`.gitignore` blocks `*.p12`, `*.jks` and `*.keystore` as a safety net). Store the keystore and its
password in two separate safe places, for example a password manager and an encrypted offline drive.

Print the certificate fingerprints; the SHA-1 goes into the Android OAuth client (GOOGLE_CLOUD_SETUP.md),
the SHA-256 is what every signed APK must show:

```
keytool -list -v -keystore mensis-app-signing.p12 -alias mensis
```

### 2. Enrol it in Play App Signing (once, before the first upload)

The choice of app signing key is made when the **first** bundle is uploaded to any track and cannot
be undone later, so do this before uploading anything:

1. Play Console → create the app (default language English, app, free).
2. Test and release → App integrity → App signing (or the prompt when you create the first release) →
   choose to use your own app signing key and to export and upload it from a Java keystore.
3. Download the PEPK tool and the encryption public key from that page and run the command the page
   shows, pointing it at `mensis-app-signing.p12` and alias `mensis`. Upload the resulting encrypted ZIP.
4. When the page offers it, also register the upload key's certificate:

   ```
   keytool -export -rfc -keystore <your existing keystore> -alias <its alias> -file upload_certificate.pem
   ```

5. Check that the "App signing key certificate" SHA-256 shown afterwards equals the one from step 1.

### 3. Put the upload key and the web client ID into GitHub (once)

Run these in the repository directory. `gh secret set` without `--body` asks for the value, so
passwords do not end up in the shell history:

```
base64 < /path/to/existing-upload.keystore | gh secret set UPLOAD_KEYSTORE_B64
gh secret set UPLOAD_KEYSTORE_PASSWORD
gh secret set UPLOAD_KEY_ALIAS
gh secret set UPLOAD_KEY_PASSWORD
gh variable set MENSIS_GOOGLE_WEB_CLIENT_ID --body "<web client ID>.apps.googleusercontent.com"
```

The web client ID is public (GOOGLE_CLOUD_SETUP.md) and is therefore a variable, not a secret. A tag
build fails if any of the five is missing.

### 4. GitHub Pages (once)

The privacy policy is `docs/privacy.md`, served at <https://ritwikshanker.github.io/mensis/privacy/>.
Pages is set to deploy from the `main` branch, folder `/docs` (repository Settings → Pages). Play
Console's privacy policy field and the Google Cloud consent screen use that URL; the app opens it from
About → Privacy policy.

## Version numbers

The version comes from the git tag on the built commit (`app/build.gradle.kts`):

| Tag | versionName | versionCode |
|---|---|---|
| `v0.1.0` | 0.1.0 | 100 |
| `v1.2.3` | 1.2.3 | 10203 |
| none | 0.0.0-dev+<commit> | 1 |

versionCode is `major × 10000 + minor × 100 + patch`; minor and patch stay below 100. Check with
`./gradlew -q :app:printVersion`.

## Making a release

1. `main` is green: the CI and Instrumented tests workflows passed on the commit to be released.
2. Write the release notes as `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (at most
   500 characters, Play's limit; no wording that suggests contraceptive or diagnostic reliability).
   Update the other store texts in `fastlane/metadata/android/en-US/` if features changed. Commit
   (`docs: add changelog for vX.Y.Z`) and push.
3. Tag and push:

   ```
   git tag -a vX.Y.Z -m "Mensis X.Y.Z"
   git push origin vX.Y.Z
   ```

4. The **Release** workflow checks the tag, changelog, secrets and web client ID, runs ktlint, detekt,
   unit tests and lint, builds the bundle signed with the upload key and the unsigned APK, and creates a
   **draft** GitHub release with the unsigned APK and the changelog as notes. The bundle, the unsigned
   APK and the R8 mapping file are kept as the workflow artifact `release-X.Y.Z`.
5. **Play:** download the artifact (`gh run download <run id> -n release-X.Y.Z`) and upload
   `mensis-X.Y.Z.aab` to the internal testing track first. Look at the pre-launch report, then promote.
6. **GitHub APK:** sign it with the app signing key and add it to the draft:

   ```
   scripts/finish-github-release.sh vX.Y.Z /path/to/mensis-app-signing.p12 mensis
   ```

   The script downloads the unsigned APK, checks its alignment (16 KB pages for SQLCipher's native
   libraries), signs it with `apksigner` (which asks for the password), verifies it and prints the
   certificate. Compare the SHA-256 with Play Console's app signing certificate before answering `y`;
   the script then uploads `mensis-X.Y.Z.apk` and `SHA256SUMS` and removes the unsigned APK.
   Signing alone, without GitHub: `scripts/sign-release-apk.sh <unsigned.apk> <keystore> <alias> <out.apk>`.
7. Check the signed APK on the phone: install it over the previous release (`adb install -r`), open
   the app, confirm that the data is still there and that About shows the new version.
8. Privacy check on the signed APK, with `aapt2` from the Android SDK's build-tools:

   ```
   aapt2 dump permissions mensis-X.Y.Z.apk
   ```

   Expected: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED,
   SCHEDULE_EXACT_ALARM, USE_BIOMETRIC, USE_FINGERPRINT, WAKE_LOCK and the app's own
   `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Anything else needs an explanation before release.
9. Publish the GitHub release (`gh release edit vX.Y.Z --draft=false`) and roll out on Play.

## Dry run

Actions → Release → Run workflow (on any branch) runs the whole pipeline without secrets: it generates
throwaway upload and app signing keys, builds and signs the bundle and the APK (the APK with
`scripts/sign-release-apk.sh`), and uploads the artifacts. It creates no release.
