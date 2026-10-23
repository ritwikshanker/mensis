# Google Cloud setup for Drive backup

Drive backup needs an OAuth configuration in a Google Cloud project owned by whoever builds and
publishes the app. Without it the app builds and runs normally; the Backup screen then says that Drive
backup is not configured in this build, and export and import keep working.

What Mensis asks Google for:

- **Sign in with Google** (Credential Manager) to pick the account. Mensis reads the e-mail address
  from the returned credential and sends the credential nowhere.
- **One scope, `https://www.googleapis.com/auth/drive.appdata`**: Drive's hidden app-data folder. The
  app cannot see or change any other file in the user's Drive, and the folder is not visible in the
  Drive UI.
- Calls to the **Google Drive API v3** to list, upload, download and delete files in that folder.

Console menu names change from time to time; the steps below name what to look for.

## 1. Project and API

1. Create a project at <https://console.cloud.google.com/> (for example "Mensis").
2. APIs & Services → Library → **Google Drive API** → Enable.

## 2. OAuth consent screen

In APIs & Services → OAuth consent screen (shown as "Google Auth Platform" in newer consoles):

1. **Branding**: app name `Mensis`, a user-support e-mail, a developer contact e-mail, the home page
   (the GitHub repository or the GitHub Pages site) and the **privacy policy URL**
   <https://ritwikshanker.github.io/mensis/privacy/> (`docs/privacy.md`). The console saves these links
   only after `ritwikshanker.github.io` is added under Authorized domains. Leave the logo empty unless
   you want to go through brand review for it.
2. **Audience**: user type **External**. While the app is in *Testing*, add the Google accounts that
   may sign in as test users.
3. **Data access**: add the scope `.../auth/drive.appdata` ("See, create, and delete its own
   configuration data in your Google Drive"). No other scope is needed. Google lists `drive.appdata`
   as a non-sensitive scope (Drive API guide "Choose Google Drive API scopes"), and the Data Access page
   shows it under "Your non-sensitive scopes" (22 October 2026), so the app needs no scope verification.

## 3. OAuth clients

Credentials → Create credentials → OAuth client ID, four times at most:

| Client type | For | Values |
|---|---|---|
| Android | debug builds | package `com.deutschdreamers.mensis`, SHA-1 of the debug keystore |
| Android | builds installed from Google Play | package `com.deutschdreamers.mensis`, SHA-1 of the **app signing key** |
| Android | signed release builds installed directly, not through Play (optional) | package `com.deutschdreamers.mensis`, SHA-1 of the **upload key** |
| Web application | the build | name e.g. "Mensis sign-in"; no origins or redirect URIs |

- Debug SHA-1, from the standard debug keystore:

  ```
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
  ```

  or `./gradlew :app:signingReport`. Every machine that builds debug APKs for testing has its own
  debug keystore unless you share one; add one Android client per debug keystore you use.
- Release SHA-1: Google Play re-signs uploads with the **app signing key**, so use that certificate's
  SHA-1 from Play Console (Test and release → App integrity → App signing, available after the first
  upload), not the upload key's (RELEASING.md).
- An APK from Android Studio's Generate Signed App Bundle or APK, installed on a phone directly, is
  signed with the upload key instead. Sign-in from such a build needs its own Android client with the
  upload key's SHA-1 (`keytool -list -v -keystore keystore.jks`); a debug build needs no extra client.
- Android clients have no secret. They tie the package name and signing certificate to the project;
  a build signed with any other key cannot get a token.
- The **web client ID** is what the build needs: Credential Manager's "Sign in with Google" asks for a
  server client ID even though Mensis has no server. It is a public value, not a secret. Do not create
  or download a client secret for it; Mensis never uses one.

## 4. Put the web client ID into the build

The ID looks like `123456789012-abc123def456.apps.googleusercontent.com`. It is read by
`backup-drive/build.gradle.kts`:

- Locally: add to `~/.gradle/gradle.properties` (outside the repository)

  ```
  mensis.googleWebClientId=123456789012-abc123def456.apps.googleusercontent.com
  ```

  or pass `-Pmensis.googleWebClientId=…` on the command line (the environment variable
  `MENSIS_GOOGLE_WEB_CLIENT_ID` works too).

Builds without it report Drive backup as "not configured in this build"; a signed release build without
it stops with an error.

## 5. Publish

While the consent screen is in *Testing*, only the listed test users (at most 100) can sign in and
Google shows an "unverified app" notice. Grants made by test users expire seven days after consent for
scopes beyond name, e-mail and profile, so `drive.appdata` is affected: test devices are asked to
reconnect after a week (Mensis then shows "Google account needs reconnecting" and posts a
notification). Before release, publish the app (Audience → Publish app, status "In production").
With only non-sensitive scopes, Google does not require app verification; showing the app name and
logo on the consent screen goes through the lighter brand verification, for which the home page and
privacy policy URL must be on a domain you have verified. Follow the console's prompts, which reflect
Google's current rules.

## 6. Check it works

1. Install a debug build made with the web client ID set.
2. Settings → Backup → Turn on backup → Continue with Google: the account sheet appears, then a
   consent screen naming Mensis and "its own configuration data in your Google Drive".
3. Choose a password; "Backing up…" turns into "Backed up just now".
4. On another phone or after Delete all data: onboarding → Restore from backup finds the backup.

If the account sheet never appears, sign-in fails at once, or choosing the account just returns to
the backup screen (Google reports an unregistered signing key as a cancelled sign-in), compare the
installed build's package name and signing SHA-1 with the Android OAuth clients, and check that the
web client ID belongs to the same project. Users can see and remove Mensis's access at <https://myaccount.google.com/permissions>.
