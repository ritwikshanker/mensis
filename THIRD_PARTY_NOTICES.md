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
| Kotlin standard library, kotlinx.coroutines (incl. its Play services integration), kotlinx.serialization | Apache-2.0 |
| Dagger / Hilt | Apache-2.0 |
| SQLCipher for Android (net.zetetic), with SQLite and LibTomCrypt inside | BSD-3-Clause; SQLite and LibTomCrypt are public domain |
| Protocol Buffers (repackaged in DataStore as `datastore-preferences-external-protobuf`) | BSD-3-Clause |
| OkHttp, Okio | Apache-2.0 |
| Bouncy Castle | MIT |
| Google Play services auth, Google Identity (googleid) | Android Software Development Kit License |

The app shows this file under About and privacy → Open-source libraries, followed by the full texts of
the Apache License 2.0 and the SIL Open Font License 1.1 (both bundled with the app). The licences that
are not reproduced elsewhere follow.

## SQLCipher for Android

Copyright (c) 2008-2023, ZETETIC LLC. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided
that the following conditions are met:

- Redistributions of source code must retain the above copyright notice, this list of conditions and the
  following disclaimer.
- Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
  the following disclaimer in the documentation and/or other materials provided with the distribution.
- Neither the name of the ZETETIC LLC nor the names of its contributors may be used to endorse or promote
  products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

SQLite is in the public domain. LibTomCrypt is released under the LibTom license, a public-domain
dedication (https://github.com/libtom/libtomcrypt/blob/develop/LICENSE).

## Protocol Buffers

Copyright 2008 Google Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided
that the following conditions are met:

- Redistributions of source code must retain the above copyright notice, this list of conditions and the
  following disclaimer.
- Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
  the following disclaimer in the documentation and/or other materials provided with the distribution.
- Neither the name of the copyright holder nor the names of its contributors may be used to endorse or
  promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

## Bouncy Castle

Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org).

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish, distribute, sub license, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
following conditions: The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
USE OR OTHER DEALINGS IN THE SOFTWARE.
