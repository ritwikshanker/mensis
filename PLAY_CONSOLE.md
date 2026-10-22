# Google Play Console

What to enter in Play Console for Mensis, and why. Based on Google's help pages as read on
22 October 2026 (listed under Sources); Play changes its forms, so where the console's wording differs,
the console wins and this file should be updated. Items marked **check** could not be confirmed from an
official page.

Before the first upload, set up app signing as described in RELEASING.md: the choice is final once a
bundle has been uploaded.

## Create the app

- App name `Mensis: private cycle tracker` (`title.txt`), default language English (United States),
  App, Free.
- Category **Health & Fitness**. Contact e-mail: required, shown on the store page and named in the
  privacy policy as a contact. Website: <https://ritwikshanker.github.io/mensis/>.

## Store listing

The texts and graphics live in `fastlane/metadata/android/en-US/` (fastlane `supply` layout, also read by
F-Droid). Copy them into the console, or upload them with `fastlane supply` once a Play service account is
set up.

| Console field | File | Limit |
|---|---|---|
| App name | `title.txt` | 30 characters |
| Short description | `short_description.txt` | 80 characters |
| Full description | `full_description.txt` | 4000 characters |
| Release notes | `changelogs/<versionCode>.txt` | 500 characters |
| App icon | `images/icon.png` | 512 × 512, 32-bit PNG with alpha, at most 1024 KB |
| Feature graphic | `images/featureGraphic.png` | 1024 × 500, JPEG or 24-bit PNG without alpha; required |
| Phone screenshots | `images/phoneScreenshots/` (to be added) | at least 2 (4 at 1080 px or more for recommendations), 320–3840 px, long side at most twice the short side |

- The icon is the launcher icon's app tile; the feature graphic combines it with the wordmark and the
  welcome subtitle.
- **Screenshots are still missing.** Take them on the phone with synthetic data only (import the
  synthetic 8-cycle CSV): turn off Settings → Hide in recent apps first, because it blocks screenshots.
  Suggested: Today, Calendar, Insights, Daily log, Backup, About ("What leaves this phone").
- Metadata policy: no emojis or repeated special characters, no ALL CAPS, no "best", "#1", "top",
  "new", "free" or ranking claims in the title or icon.
- Health apps that are not medical devices need a disclaimer in the description that the app "is not a
  medical device and does not diagnose, treat, cure, or prevent any medical condition", and a reminder
  to consult a healthcare professional. The "Important" paragraph of `full_description.txt` carries
  both. **Check** whether the review also wants the healthcare-professional sentence inside the app; the
  About screen currently says "Mensis is not a medical device and does not provide medical advice or
  contraception."
- Nothing in any text may suggest contraceptive or diagnostic reliability (engineering rules).

## App content (Policy → App content)

### Privacy policy

<https://ritwikshanker.github.io/mensis/privacy/> (`docs/privacy.md` on GitHub Pages). Play requires
the policy on a public, non-geofenced URL that is not a PDF, and also a link or text inside the app:
About → Privacy policy opens the same page.

### Ads

No, the app contains no ads. Advertising ID: the app does not use it (no `AD_ID` permission in the
merged manifest).

### App access

All functionality is available without special access. There is no account. The app lock is optional
and uses the phone's own screen lock; Drive backup works with any Google account the reviewer signs in
with.

### Content rating

Fill in the IARC questionnaire honestly; Mensis has no violence, gambling, user-to-user communication,
location sharing or purchases. The daily log can record sexual activity as a health fact (a "Sex" field
with no imagery or explicit text); answer the sexual-content questions with that in mind.

### Target audience and content — your decision

Menstrual tracking is relevant to people under 18, but the choice has policy consequences:

| Choice | Consequence |
|---|---|
| **18 and over only** | Play enables "Restrict Minor Access": users under 18 can neither find nor install the app on Play. Simplest for policy |
| Include 13–15 and/or 16–17 | These groups "may be considered to include children in some locales", which brings in the Families policy and the mixed-audience rules. Mensis has no ads, but the rules also cover SDKs not approved for child-directed services; **check** whether Google Sign-In and the Drive API count as approved before choosing this |

The original plan chose 18 and over. Whatever you choose, keep the store listing and the privacy policy
consistent with it.

### Data safety

Answer **No** to "Does your app collect or share any of the required user data types?" The form still has
to be submitted. Reasoning, in Google's own terms:

- "Collect" means transmitting data off the device. Everything Mensis records is processed only on the
  phone, which is exempt.
- **Drive backup** is uploaded off the device, but encrypted on the phone with a key derived from the
  user's password (Argon2id, AES-256-GCM), so it is "unreadable by you or anyone other than the sender
  and recipient", including Google: the end-to-end encryption exemption. Google's FAQ also exempts
  uploads straight to the user's own cloud storage when the app never collects the data. The privacy
  policy explains the backup in full.
- **Export, doctor report and share sheet** are transfers the user starts and expects, which the
  sharing definition exempts ("based on a specific user-initiated action").
- **Google sign-in**: the account e-mail is read from the credential on the phone and stored there; the
  app sends it nowhere.
- **Check**: the backup file's Drive properties are not encrypted: format and schema versions, the phone
  model, a key id and a content fingerprint, plus Google's own size and time. None of them is health or
  personal information, and they go to the user's own Drive, not to the developer; if you read the form
  differently for the phone model, declare it as collected (not shared), optional, for app
  functionality, encrypted in transit.
- "Data encrypted in transit" and the deletion questions appear only after a Yes. A data-deletion URL is
  required only for apps with accounts; Mensis has none. Deletion happens in the app (Settings → Delete
  all data, optionally including the Drive backups).

### Health apps declaration

Policy → App content → Health apps: declare **Period Tracking** (Health and fitness group: menstrual
cycle tracking, ovulation tracking and prediction, fertility awareness). **Check** whether the pill,
patch, ring and injection reminders also call for "Medication and Treatment Management"; Mensis does not
manage treatment beyond reminders the user sets. Mensis does not use Health Connect.

### Other declarations

- Government app, financial features, news app: no.
- Exact alarms: `SCHEDULE_EXACT_ALARM` is granted by the user and needs no declaration; Mensis does not
  use `USE_EXACT_ALARM`, which does.
- Foreground services: none. WorkManager's `FOREGROUND_SERVICE` permission is removed from the manifest
  because no worker runs in the foreground, so there are no foreground service types to declare.

## Technical requirements

- targetSdk 37, minSdk 26.
- 16 KB page size: AGP 9.4.1 keeps native libraries uncompressed and 16 KB-aligned, and SQLCipher for
  Android supports 16 KB pages since 4.6.1 (Mensis uses 4.19.1). `scripts/sign-release-apk.sh` checks
  the APK's alignment with `zipalign -c -P 16`.
- Release notes per version: `changelogs/<versionCode>.txt`, checked by the release workflow.

## Testing tracks

Start with the internal testing track and read the pre-launch report. **Check** in the console whether
your developer account must run a closed test (a number of testers over a number of days) before it can
apply for production access; new personal accounts have had such a requirement.

## Sources

- Data safety: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Account deletion requirement: <https://support.google.com/googleplay/android-developer/answer/13327111>
- User data and privacy policy: <https://support.google.com/googleplay/android-developer/answer/10144311>
- Health apps declaration: <https://support.google.com/googleplay/android-developer/answer/14738291>
- Health content and services: <https://support.google.com/googleplay/android-developer/answer/12261419>
- Target audience: <https://support.google.com/googleplay/android-developer/answer/9867159>
- Families policy, mixed audience: <https://support.google.com/googleplay/android-developer/answer/9893335>
- Foreground service declaration: <https://support.google.com/googleplay/android-developer/answer/13392821>
- Exact alarm permission: <https://support.google.com/googleplay/android-developer/answer/9888170>
- Store listing limits: <https://support.google.com/googleplay/android-developer/answer/9859152>
- Release notes: <https://support.google.com/googleplay/android-developer/answer/9859348>
- Metadata policy: <https://support.google.com/googleplay/android-developer/answer/9898842>
- Graphic assets: <https://support.google.com/googleplay/android-developer/answer/9866151>
- 16 KB page sizes: <https://developer.android.com/guide/practices/page-sizes>
- SQLCipher 16 KB support: <https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/>
- fastlane supply: <https://docs.fastlane.tools/actions/supply/>
- F-Droid metadata: <https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/>
