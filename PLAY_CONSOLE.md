# Google Play Console

What to enter in Play Console for Mensis, and why. Based on Google's help pages as read on
22 October 2026 (listed under Sources); Play changes its forms, so where the console's wording differs,
the console wins and this file should be updated. Items marked **check** could not be confirmed from an
official page.

On the first upload, keep Play App Signing's default (Google-generated app signing key, `keystore.jks`
as upload key; RELEASING.md): the choice is final once a bundle has been uploaded.

## Create the app

- App name `Mensis: private cycle tracker` (`title.txt`), package `com.deutschdreamers.mensis`, default
  language English (United States), App, Free.
- The create form offers "automatic protection": Play adds an installer check to the app's code, and
  users who got the app from another source are asked to get it from Google Play. It was left on.
- Category **Health & Fitness** (Store settings). Contact e-mail: required, shown on the store page and
  named in the privacy policy as a contact. Website: <https://ritwikshanker.github.io/mensis/>.

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
| Phone screenshots | `images/phoneScreenshots/` (7 PNGs, 1080 × 1920) | at least 2 (4 at 1080 px or more for recommendations), 320–3840 px, long side at most twice the short side |

- The icon is the launcher icon's app tile; the feature graphic combines it with the wordmark and the
  welcome subtitle.
- The phone screenshots are drawn from the real screens with the synthetic preview data by the
  instrumented test `StoreScreenshots` (CONTRIBUTING.md, "Store screenshots"): Today, Daily log,
  Calendar, Insights, Backup, About and Today in dark mode, as PNGs without an alpha channel. The README
  shows the same files. Redraw them when one of these screens changes.
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

### Sign in details (formerly App access)

"Is any part of your app restricted?" No. There is no account. The app lock is optional and uses the
phone's own screen lock; Drive backup works with any Google account the reviewer signs in with.

### Content rating

Category "All Other App Types", every question answered No. Mensis has no violence, gambling,
user-to-user communication, location sharing or purchases. The daily log records sexual activity only as
a clinical health fact (a "Sex" field with None / Protected / Unprotected, no imagery or suggestive
text), which was not counted as sexual content. Result: ESRB Everyone, PEGI 3, USK 0, IARC 3+.

### Target audience and content

Target age group: **18 and over** only. Play does not restrict minors by itself: the form has an
optional box "Restrict users that Google has determined to be minors from my app", which is left
unticked so that Play blocks nobody it guesses to be under 18.

Including 13–15 and/or 16–17 would bring in the Families policy and the mixed-audience rules ("may be
considered to include children in some locales"). Mensis has no ads, but the rules also cover SDKs not
approved for child-directed services; **check** whether Google Sign-In and the Drive API count as approved
before changing this. Keep the store listing and the privacy policy consistent with the choice.

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

Policy → App content → Health apps: declare **Period tracking** (Health and fitness group: menstrual
cycle tracking, ovulation tracking and prediction, fertility awareness) and **Medication and treatment
management** (Medical group; Google's description includes medication reminders, and Mensis has pill,
patch, ring and injection reminders). Mensis does not use Health Connect.

### Other declarations

- Government app, financial features, news app: no.
- Exact alarms: `SCHEDULE_EXACT_ALARM` is granted by the user and needs no declaration; Mensis does not
  use `USE_EXACT_ALARM`, which does.
- Foreground services: none. WorkManager's `FOREGROUND_SERVICE` permission is removed from the manifest
  because no worker runs in the foreground, so there are no foreground service types to declare.

## Technical requirements

- targetSdk 37, minSdk 26.
- 16 KB page size: AGP 9.4.1 keeps native libraries uncompressed and 16 KB-aligned, and SQLCipher for
  Android supports 16 KB pages since 4.6.1 (Mensis uses 4.19.1), so nothing extra is needed;
  `zipalign -c -P 16 4 <apk>` checks an APK's alignment.
- Release notes per version: `changelogs/<versionCode>.txt`, at most 500 characters.

## Testing tracks

Start with the internal testing track and read the pre-launch report. The developer account is a
personal one, and the app dashboard requires a closed test before production: publish a closed testing
release, have at least 12 testers opted in, and run the test with them for at least 14 days. Then apply
for production access, which asks questions about the closed test.

Drive sign-in for testers outside the consent screen's test-user list needs the consent screen
published (GOOGLE_CLOUD_SETUP.md, step 5) and the release Android OAuth client with Play's app signing
SHA-1.

### Testers for the closed test

A track takes either e-mail lists or Google Groups, not both. The closed test uses e-mail lists, so
invited testers only opt in and install. The README and the project site also let anyone ask to join
through an open Google Group, which serves only as a sign-up list:

1. The group `mensis-testers` (Mensis Google account, <https://groups.google.com/>): anyone can join;
   only managers can view members (testers' addresses stay private); only managers can post;
   conversation history off.
2. Play Console → Test and release → Testing → Closed testing → the track's Testers tab: Email lists,
   with *Friends*, *Internal Testers* and *Group sign-ups* ticked. Friends and Internal Testers also
   feed the internal testing track, so people from the group go only into *Group sign-ups* (created
   with the Mensis account as its first address; a list cannot be empty). Feedback channel: the GitHub
   issues page. Changing which lists the track uses is a change that goes through review (Publishing
   overview).
3. Once a day, compare the group's members with *Group sign-ups* and add the new addresses (the arrow
   next to the list on the Testers tab opens it). The README promises this usually within a day.
4. Copy the opt-in link the Testers tab shows. The README and `docs/index.md` use
   <https://play.google.com/apps/testing/com.deutschdreamers.mensis>; check that the console shows the
   same link.
5. Someone on one of the lists who opts in with that Google account is a tester; they install Mensis
   from Google Play. They count toward the 12 opted-in testers once they have opted in.

## Sources

- Automatic protection: <https://support.google.com/googleplay/android-developer/answer/10183279>
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
