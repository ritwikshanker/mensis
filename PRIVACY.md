# Privacy and threat model

This document describes what Mensis stores, what can leave the phone, how the data is protected and
what that protection does not cover. It describes the code in this repository; if the two disagree,
the code is wrong and an issue is welcome.

## What is stored, and where

| Data | Where | Protection |
|---|---|---|
| Daily logs, contraception history, tracker settings and reminders | SQLCipher database in the app's private storage | AES-256 (SQLCipher) with a random 256-bit key |
| Database key | `files/keys/db.key` | Wrapped with an AES-256-GCM key in the Android Keystore (`mensis/db-wrap/v1`) |
| Backup key (only with Drive backup on) | `files/keys/backup.key` | Wrapped with a second Keystore key (`mensis/backup-wrap/v1`) |
| Theme, app lock, screenshot blocking, onboarding done | `ui_prefs` DataStore | App-private storage, no health data |
| Backup settings: Google account address, frequency, Wi-Fi only, time and size of the last backup, last error | `backup_prefs` DataStore | App-private storage, no health data |

Cycles, phases and predictions are never stored; they are computed from the daily logs each time.

### Keys

- The **database key** is 32 random bytes from `SecureRandom`, created on first start. It is passed
  to SQLCipher as a raw key, so SQLCipher's own password derivation is skipped (a random key gains
  nothing from it).
- Both **wrapping keys** are AES-256-GCM keys generated inside the Android Keystore. They never leave
  it. StrongBox is used when the phone has one, the TEE otherwise.
- The wrapping keys are **not bound to user authentication**. This is deliberate: reminders and
  scheduled backups must run while the phone is locked or the app is closed. The consequence is
  discussed under "What this does not protect against".
- Android's own backup and device-to-device transfer are disabled (`allowBackup="false"`, data
  extraction rules exclude everything). A copy of the database is useless without this phone's
  Keystore, so Mensis's own Drive backup is the supported way to move data.

### App lock and screen privacy

- The app lock uses the phone's fingerprint, face or screen-lock PIN through `BiometricPrompt`. It is a
  UI gate: it decides whether the app shows its screens, not whether the database can be decrypted.
- The app locks again after a configurable time in the background (immediately, 1, 5 or 15 minutes,
  or never).
- "Hide in recent apps" (on by default) sets `FLAG_SECURE`: no screenshots, screen recordings or
  Recents thumbnails of the app.

## What leaves the phone

**Nothing, by default.** Mensis has no analytics, crash reporting, ads, remote configuration or
tracking SDKs, and no network code outside the `:backup-drive` module. Cleartext (HTTP) traffic is
disabled for the whole app.

### Files you export

An export is written only where you choose in the system file picker. A JSON or CSV export without a
password is plain text that anyone with the file can read; the export screen says so. With a password,
the file is sealed in the same container as Drive backups (below).

### Google Drive backup (optional)

When you turn on Drive backup:

1. **Sign-in.** Credential Manager's "Sign in with Google" identifies the account. Mensis keeps the
   e-mail address locally and does not send the returned credential anywhere.
2. **Authorization.** Google Play services' `AuthorizationClient` grants one scope,
   `drive.appdata`: the hidden app-data folder in your Drive. Mensis cannot see any other file.
3. **Encryption on the phone.** Your backup password goes through **Argon2id** (64 MiB of memory,
   3 passes, 1 lane, a random 16-byte salt) to an AES-256 key. The complete JSON export is gzipped
   and encrypted with **AES-256-GCM** under that key, with a random 96-bit nonce per file. The file
   header (format version, Argon2 parameters, salt) is authenticated as GCM associated data, so any
   change to it makes the file unreadable. The format is documented in `docs/export-format.md`.
4. **Upload.** The encrypted file is uploaded to the app-data folder as `mensis-backup.enc` over
   HTTPS, then checked against the size and MD5 that Drive reports. Mensis keeps the two newest
   backups made with the current password and deletes older ones; after a password change, backups
   under the old password are deleted once a backup under the new one is safely stored.

The **password is never stored**. The derived key is kept, wrapped by the Keystore, so scheduled
backups run without asking for the password. Losing the password means losing access to the backups:
there is no reset and no recovery, by design.

**What Google can see:** that the account uses Mensis's app-data folder, the encrypted file, its
size, its creation time and these file properties: the export format and database schema versions,
the phone model that made the backup (shown in the restore screen), a key id (a hash of the salt,
which is in the file header anyway) and a content fingerprint. The fingerprint is an HMAC under a key
derived from the backup key, so it reveals nothing about the content without the password; Mensis
uses it to skip uploads when nothing has changed. Google sees no health data in readable form.

**Restore** downloads the newest backup, checks its size and MD5, decrypts it with the password on the
phone and replaces everything on the phone in one database transaction. The key the password gives is
kept, so backups continue under the same password.

**Turning backup off** cancels scheduled backups, deletes the backup key and the account address from
the phone and revokes Mensis's Drive access. Backups already in Drive stay there until you delete
them (Backup → Delete backups from Drive) or remove Mensis's access in your Google account.

Without Google Play services, Drive backup is unavailable; everything else, including export and
import, works.

## Logs

Mensis writes no health data to logs. Release builds strip all `android.util.Log` calls with R8
(`-assumenosideeffects`), including those of libraries.

## What this protects against

- **A lost or stolen phone that is locked.** The data is protected by Android's device encryption
  and, in addition, by SQLCipher with a key that only this phone's Keystore can unwrap.
- **Someone picking up the unlocked phone.** The app lock asks for the phone's fingerprint or PIN before
  showing anything.
- **Shoulder surfing and screenshots.** `FLAG_SECURE` blocks screenshots and the Recents thumbnail.
- **The cloud provider.** Google stores only ciphertext and the metadata listed above.
- **Tampered or damaged backups.** AES-GCM authentication rejects any altered file; the download is
  also checked against Drive's checksum.
- **Guessing the backup password from a stolen backup file.** Argon2id makes every guess cost 64 MiB
  of memory and three passes. This only helps with a good password: Mensis asks for at least 12
  characters and shows a strength estimate, but cannot stop a weak one.

## What this does not protect against

- **Someone who knows the phone's PIN** can open the app (the app lock accepts the screen-lock PIN)
  and can use the phone as you.
- **A compromised phone.** Malware with root access, or a forensic tool that can unlock the phone, can
  read the app's memory and use the Keystore keys, because they are not bound to user authentication
  (so that reminders and backups work in the background).
- **A weak backup password.** Argon2id slows guessing; it does not make a short or common password
  safe.
- **Metadata.** Backups reveal to Google that you use Mensis, roughly how much data you keep (the file
  size) and when backups happen. Notifications reveal that the app is installed; their content is
  hidden on the lock screen.
- **Your Google account.** Someone with access to your Google account can download or delete the
  encrypted backups, but cannot read them without the backup password.
- **Exports without a password**, once they are outside the app.

Mensis is not a medical device and does not provide medical advice or contraception.
