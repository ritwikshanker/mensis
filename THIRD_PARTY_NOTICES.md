# Third-party notices

## Fonts (downloaded at build time into `app/build/generated/designAssets`)

- **Roboto Flex** — Copyright 2017 The Roboto Flex Project Authors. SIL Open Font License 1.1 (the licence
  text ships in the app as `res/raw/ofl_roboto_flex.txt`). Source: https://github.com/google/fonts/tree/main/ofl/robotoflex
- **Source Serif 4** — Copyright 2014-2023 Adobe. SIL Open Font License 1.1 (`res/raw/ofl_source_serif_4.txt`).
  Source: https://github.com/google/fonts/tree/main/ofl/sourceserif4

## Icons (downloaded at build time as `res/drawable/ms_*.xml`)

- **Material Symbols Rounded** — Copyright Google LLC. Apache License 2.0.
  Source: https://github.com/google/material-design-icons (`symbols/android`). The list of icons is
  `app/material-symbols.txt`.

## Libraries

Resolved by Gradle from Google Maven and Maven Central; versions in `gradle/libs.versions.toml`.

| Library | Licence |
|---|---|
| AndroidX (Compose, Navigation 3, Lifecycle, Room, DataStore, WorkManager, Biometric, Credentials, Core) | Apache-2.0 |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| Dagger / Hilt | Apache-2.0 |
| SQLCipher for Android (net.zetetic) | BSD-3-Clause |
| OkHttp | Apache-2.0 |
| Bouncy Castle | MIT |
| Google Play services auth, Google Identity (googleid) | Android Software Development Kit License |

The in-app "Open-source libraries" screen (build step 10) renders this file.
