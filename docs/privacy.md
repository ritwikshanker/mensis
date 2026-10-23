---
title: Privacy policy
permalink: /privacy/
---

# Mensis privacy policy

Effective 23 October 2026.

Mensis is a menstrual cycle tracker for Android, developed by Ritwik Shanker and published as open
source at <https://github.com/ritwikshanker/mensis>. This policy covers the Mensis app published on
Google Play.

## In short

- Everything you log stays on your phone, in an encrypted database.
- Mensis has no account, and its code contains no analytics, crash reporting, ads or tracking.
- The developer runs no server and receives no data from the app.
- Data leaves your phone only when you choose it: an encrypted backup to your own Google Drive, a file
  you export, or a report you share.
- The version on Google Play also contains Google Play's automatic protection, which Google adds to the
  app and which shares anonymised environment and performance data with Google (see below).

## What Mensis stores on your phone

- What you log: period days and flow, symptoms, mood, sleep, notes, waking temperatures, cervical mucus,
  LH and pregnancy tests, contraception method and dates, pregnancy mode.
- Your settings: cycle and period length estimates, what to track, units, reminders, theme, app lock
  and backup settings. With Drive backup on, also the Google account address you chose and the time,
  size and outcome of the last backup.

Cycles, phases and predictions are calculated from your entries each time and are not stored.

The data is kept in a SQLCipher-encrypted database in the app's private storage. Its key is random and
protected by a key in the phone's Android Keystore, so a copy of the database cannot be read on another
device. Android's own app backup and device-to-device transfer are turned off for Mensis.

## What leaves your phone, and only if you choose it

**Google Drive backup (off unless you turn it on).** You sign in with your Google account and allow
Mensis to use the hidden app-data folder of your Drive; Mensis cannot see any other file there. Before
upload, the backup is encrypted on the phone with a key derived from a password only you know
(Argon2id and AES-256-GCM). Google stores the encrypted file, its size and time, and technical file
properties (format version, phone model, a key identifier and a content fingerprint that reveal nothing
about your entries). Neither Google nor the developer can read the backup without your password. The
password is never stored or sent; if you lose it, the backup cannot be recovered. Sign-in and Drive are
provided by Google under your Google account and
[Google's privacy policy](https://policies.google.com/privacy).

**Exports.** An export is written only to the place you pick in the system file picker. Without a
password it is a readable JSON or CSV file; with a password it is encrypted like a Drive backup.

**Doctor report.** The PDF is created on the phone and saved or shared only where you choose. It is not
encrypted. Notes are left out unless you include them.

**Links.** Opening the source code, this policy or the issue tracker from the app hands the address to
your browser; the app itself sends nothing.

Apart from Google sign-in and the Drive calls for backup and restore, Mensis's own code makes no network
connections.

## Google Play's automatic protection

The Mensis version distributed on Google Play includes Google Play's automatic protection. Google adds
it to the app when it prepares the app for distribution; it is not part of Mensis's source code.
According to [Google's description](https://support.google.com/googleplay/android-developer/answer/10183279):

- it checks that the app was installed from Google Play, and a copy installed from elsewhere asks you to
  get Mensis from Google Play;
- the check can occasionally need a data connection, when the Play Store app on the phone has been
  offline for a long time;
- it shares anonymised environment and performance data with Google.

This processing is done by Google under [Google's privacy policy](https://policies.google.com/privacy).
Builds made from the source code do not contain it.

## What the developer receives

No data from the app. There is no developer server, and Mensis's code contains no analytics,
crash-reporting, advertising or tracking code. Like for every app on Google Play, Play Console shows
the developer statistics collected by Google Play, such as install numbers and crash reports from
phones whose owners allow sharing usage and diagnostics data with Google. If you report a problem on
GitHub, only what you write in the report is shared, under GitHub's terms.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | Google sign-in and Drive backup, only when backup is on |
| Notifications | Reminders you turn on, and a notice when Drive needs you to sign in again |
| Alarms and reminders, run at startup | Reminders at the time you set, kept after a restart |
| Biometrics | The optional app lock with your phone's fingerprint, face or screen lock |
| Wake lock | Reminders and scheduled backups finish while the screen is off |

## Keeping and deleting your data

Your data stays on your phone until you delete it. Settings → Delete all data removes the database, its
keys, your settings, reminders and the backup key from the phone, and can also delete your backups in
Google Drive. Uninstalling Mensis also removes its data from the phone.

Drive backups stay in your Drive until you delete them: in the app (Backup → Delete backups from Drive),
with Delete all data, or by deleting Mensis's hidden app data in Google Drive's settings under "Manage
apps". Files you exported or shared are under your control and are not affected.

## Children

Mensis collects no personal data from anyone, so it collects none from children.

## Not a medical device

Predictions are statistics about your own history. Mensis is not a medical device, does not provide
medical advice and is not a method of contraception.

## Changes and contact

Changes to this policy are published on this page with a new effective date; earlier versions are in the
project's [history](https://github.com/ritwikshanker/mensis/commits/main/docs/privacy.md). The technical
details are in [PRIVACY.md](https://github.com/ritwikshanker/mensis/blob/main/PRIVACY.md).

Questions: open an issue at <https://github.com/ritwikshanker/mensis/issues> or write to the developer
e-mail address shown on Mensis's Google Play page. Please report security issues privately through
GitHub's security advisory form.
