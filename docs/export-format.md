# Mensis export format

Settings → Export data writes one file: **JSON** (complete, re-importable) or **CSV** (one row per day,
for spreadsheets). Either can be **encrypted with a password**. The file is written on the phone to the
place you choose in the system file picker; Mensis sends it nowhere.

File names: `mensis-export-YYYY-MM-DD.json`, `.csv`, `.json.enc`, `.csv.enc`.

Units are fixed in every file: temperatures in **°C**, weights in **kg**, sleep in **minutes**. The units
chosen in Settings change only what the app displays.

## JSON

UTF-8, pretty-printed. The machine-readable schema is [`mensis-export.schema.json`](mensis-export.schema.json).

The export is lossless: every stored row is included with its id and timestamps, including days that
were cleared (`deletedAt` set). Fields without a value are left out; empty lists are written as `[]`.
Properties always appear in the order below and rows are sorted (day entries by date, method periods by
start date), so exporting the same data twice gives the same file apart from `exportedAt`.

```json
{
  "format": "mensis-export",
  "formatVersion": 1,
  "schemaVersion": 1,
  "exportedAt": "2026-10-21T12:00:00Z",
  "app": { "versionName": "1.0.0", "versionCode": 10000 },
  "settings": { ... },
  "uiPrefs": { ... },
  "contraceptionPeriods": [ ... ],
  "dayEntries": [ ... ]
}
```

| Field | Meaning |
|---|---|
| `format` | Always `mensis-export`. |
| `formatVersion` | Version of this file format. It changes only when an older reader would misread the file. Mensis upgrades older versions on import and refuses newer ones. |
| `schemaVersion` | Version of the app's database schema the file was written from (informational). |
| `exportedAt` | ISO-8601 instant. |
| `app` | Version of the app that wrote the file. |
| `settings` | Health-related settings (below). |
| `uiPrefs` | Device preferences: theme, appearance, app lock, hide in recent apps, re-lock timeout. Restored by a full backup restore only; an import never changes them. |
| `contraceptionPeriods` | Method history (below). |
| `dayEntries` | One record per calendar day (below). |

Fields may be added in later versions without a `formatVersion` change; readers must ignore fields they
do not know.

### `settings`

| Field | Values |
|---|---|
| `trackingLevel` | `period`, `symptoms`, `full` |
| `goal` | `track_cycle`, `trying_to_conceive`, `avoiding_pregnancy`, `understand_symptoms` |
| `mode` | `cycle`, `pregnancy` |
| `pregnancyLmp` | ISO date, only in pregnancy mode |
| `typicalCycleLength`, `typicalPeriodLength` | Days, from onboarding; used until real cycles exist |
| `temperatureUnit` | `celsius`, `fahrenheit` (display only) |
| `weightUnit` | `kilogram`, `pound` (display only) |
| `reminders` | Reminder settings, see below |
| `discreetWording` | Notifications show only the generic text ("Reminder" unless changed) |
| `createdAt`, `updatedAt` | ISO-8601 instants |

#### `settings.reminders`

One entry per reminder the user has changed; a missing entry or field means the default. Which reminders
apply depends on the contraception method and tracking level; entries that do not apply are kept but unused.
When each reminder fires next is derived on the phone and never stored.

| Key | Reminder |
|---|---|
| `period` | Period expected, or withdrawal bleed expected on a cyclic method |
| `fertile` | Fertile window (no hormonal method) |
| `pill`, `patch`, `ring`, `injection`, `replacement` | The current method's reminder (`replacement`: implant and IUDs) |
| `temperature` | Morning temperature (tracking level `full`) |
| `discreetTitle`, `discreetBody` | The user's own generic notification text |

Each reminder entry has `enabled` (boolean), `time` (`HH:mm`, local time) and `option`: `same_day`,
`one_day_before`, `two_days_before`, `daily`, `active_days_only`, `weekly`, `three_weeks_in_one_out`,
`four_weekly`, `twelve_weeks`, `thirteen_weeks`, `one_month_before`, `three_months_before`,
`follicular_only`. Unknown values are ignored on import.

### `contraceptionPeriods[]`

A method in effect from `startDate` to `endDate` (no `endDate` = current). Earlier periods are kept so past
cycles keep the method that applied then.

| Field | Values |
|---|---|
| `id` | Row id (UUID) |
| `method` | `none`, `combined_pill`, `progestin_only_pill`, `patch`, `ring`, `injection`, `implant`, `hormonal_iud`, `copper_iud` |
| `regimen` | `cyclic` (21 + 7) or `continuous`; cyclic methods only |
| `startDate`, `endDate`, `replaceByDate`, `checkupDate` | ISO dates |
| `dailyTime` | `HH:mm`, daily pills |
| `createdAt`, `updatedAt`, `deletedAt` | ISO-8601 instants; `deletedAt` marks a removed period |

### `dayEntries[]`

Every field except `id`, `date` and the timestamps is optional.

| Field | Values |
|---|---|
| `id` | Row id (UUID) |
| `date` | ISO date, unique |
| `flow` | `none`, `spotting`, `light`, `medium`, `heavy` |
| `temperatureC` | Waking temperature, °C, two decimals |
| `temperatureTime` | `HH:mm` |
| `symptoms` | List of `cramps`, `headache`, `bloating`, `tender_breasts`, `fatigue`, `back_pain`, `acne`, `nausea` |
| `moods` | List of `calm`, `happy`, `irritable`, `anxious`, `low`, `energetic` |
| `cervicalMucus` | `dry`, `sticky`, `creamy`, `watery`, `egg_white` |
| `lhTest`, `pregnancyTest` | `negative`, `positive` (absent = not taken) |
| `sex` | `none`, `protected`, `unprotected` |
| `pillState` | `taken`, `late`, `missed` |
| `patchChanged`, `injectionGiven`, `emergencyContraception` | `true` / `false` |
| `ringState` | `in`, `out` |
| `sleepMinutes` | Minutes |
| `weightKg` | kg, one decimal |
| `notes` | Free text |
| `createdAt`, `updatedAt` | ISO-8601 instants |
| `deletedAt` | Set when the day was cleared; the record keeps its last values |

Cycles, phases, predictions and statistics are not in the file: Mensis derives them from these facts.

## CSV

UTF-8 with a byte-order mark, comma-separated, RFC 4180 quoting (fields containing a comma, a quote or
a line break are quoted, quotes doubled), CRLF line ends. One row per logged day, sorted by date. The
header is:

```
date,flow,symptoms,moods,cervical_mucus,temperature_c,temperature_time,lh_test,pregnancy_test,sex,pill,patch_changed,ring,injection_given,emergency_contraception,sleep_minutes,weight_kg,notes
```

Values are the same words as in JSON; lists (`symptoms`, `moods`) are joined with `;`. Empty cell = not
logged. The CSV leaves out cleared days, row ids, timestamps, method history and settings; use JSON for
a complete copy. Mensis's CSV importer recognises every column of this file.

## Encrypted files (`.enc`)

"Encrypt with a password" wraps the JSON or CSV file in a container that Drive backups use as well:

| Bytes | Content |
|---|---|
| 8 | Magic `MENSISBK` (ASCII) |
| 1 | Container version, `1` |
| 1 | Key derivation, `1` = Argon2id (version 0x13) |
| 4 | Argon2 memory in KiB, big-endian (default 65536 = 64 MiB) |
| 4 | Argon2 iterations, big-endian (default 3) |
| 1 | Argon2 parallelism (default 1) |
| 16 | Random salt |
| 12 | Random AES-GCM nonce |
| rest | AES-256-GCM ciphertext of the **gzip-compressed** file, followed by the 16-byte tag |

The key is Argon2id(password as UTF-8, salt, the parameters above, 32 bytes). The first 35 bytes (magic
through salt) are the GCM associated data, so changing any header byte makes decryption fail. A wrong
password and a modified file fail in the same way; a file too short to hold a header and tag is reported
as damaged. The password is never stored; without it the file cannot be opened, by anyone.

A Drive backup (`mensis-backup.enc` in the account's hidden app-data folder) is this container around the
complete JSON export. All backups made with one password share its salt, because Mensis keeps the derived
key (wrapped in the Android Keystore) so scheduled backups need no password; each file still has its own
random nonce. Changing the backup password creates a new salt and key.

To read an encrypted export without Mensis, for example with Python and the `argon2-cffi` and
`cryptography` packages:

```python
import gzip, struct
from argon2.low_level import hash_secret_raw, Type
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

data = open("mensis-export-2026-10-21.json.enc", "rb").read()
assert data[:8] == b"MENSISBK" and data[8] == 1 and data[9] == 1
memory_kib, iterations = struct.unpack(">II", data[10:18])
parallelism, salt, nonce = data[18], data[19:35], data[35:47]
key = hash_secret_raw(b"your password", salt, time_cost=iterations, memory_cost=memory_kib,
                      parallelism=parallelism, hash_len=32, type=Type.ID)
plain = gzip.decompress(AESGCM(key).decrypt(nonce, data[47:], data[:35]))
```

## Importing

Settings → Import data (or "Import history" on a new install) reads a file on the phone. Nothing is
uploaded.

1. **Source**: CSV spreadsheet, Mensis export, drip. export or Clue export. A Mensis JSON export and a
   drip. CSV are recognised from their content whichever source is chosen. An encrypted Mensis file
   asks for its password.
2. **Map columns** (CSV only): each column gets a target, guessed from the header and, failing that, from
   the values. Slashed dates that read validly both ways (`03/04/2026`) ask whether they are day or month
   first.
3. **Preview**: rows read, valid and skipped (unreadable dates, dates in the future, rows with nothing
   to import), the first rows, values Mensis could not read, and what to do when a day already has
   data:
   - **Merge** keeps what you logged and fills only empty fields from the file;
   - **Replace** lets the file's entry overwrite the phone's for that day;
   - **Skip** leaves days that already have data untouched.
4. **Summary** with **Undo import**.

Rules that hold for every source:

- Days are matched by date. An import never deletes anything on the phone.
- The whole import is one database transaction: it applies completely or not at all.
- Importing the same file again changes nothing, under every strategy.
- **Undo** restores every row the latest import touched to its earlier state and removes the days it
  added. Only the most recent import can be undone, and edits made after it are undone with it.
- A Mensis JSON export brings its method history and settings along when the phone has none of its own,
  or when the strategy is Replace; otherwise the phone keeps its own. Device preferences are never
  imported.
- Rows sharing a date are combined: lists and notes are joined, the first row wins for single values.

### Generic CSV

Headers are matched case-insensitively, ignoring spaces and punctuation, so `Temperature (°C)` reads as
`temperature_c`. Besides Mensis's own headers, common names such as `day`, `datum`, `period`,
`bleeding`, `temp`, `bbt`, `mood`, `pain`, `mucus`, `discharge`, `lh`, `weight` and `note` are known.
The delimiter (comma, semicolon or tab) is detected from the header line. Text is read as UTF-8, UTF-16
with a byte-order mark, or Windows-1252.

| Target | Accepted values |
|---|---|
| Date | `2026-03-06`, `2026/03/06`, `20260306`, `6.3.2026` (day first), `06/03/2026` or `6-3-26` (order chosen in step 2), `6 Mar 2026`, `March 6, 2026`; a time part is ignored |
| Flow | `none`/`0`, `spotting`, `light`/`1`, `medium`/`moderate`/`2`, `heavy`/`3`; `yes`, `x` or `period` mark a period day (stored as medium) |
| Temperature °C / °F | Dot or comma decimals, optional `°C`/`°F` suffix; °F is converted to °C. Readings outside 34–43 °C are refused as implausible |
| Temperature time | `06:40`, `6.40` |
| Symptoms, Mood | Words separated by `;`, `,`, `|` or `/`, with common synonyms (`migraine` → headache, `sad` → low, `stressed` → anxious) |
| Cervical mucus | `dry`, `sticky`, `creamy`, `watery`, `egg white`/`stretchy`/`slippery` |
| LH test, Pregnancy test | `positive`/`pos`/`+`, `negative`/`neg` |
| Sex | `none`, `protected`, `unprotected` |
| Pill / Ring | `taken`, `late`, `missed` / `in`, `out` |
| Patch changed, Injection given, Emergency contraception | `true`/`yes`/`1`/`x`, `false`/`no`/`0` |
| Sleep, minutes / hours | `440` (minutes) or `7.5` (hours); `7:20` and `7 h 20 min` either way |
| Weight kg / lb | Converted to kg |
| Notes | Any text |

Values that cannot be read are left out of that day and counted in the preview.

### drip.

drip. ([bloodyhealth/drip](https://github.com/bloodyhealth/drip)) exports a CSV whose columns are the
dotted paths of its database schema (version 4): `date`, `temperature.value`, `temperature.exclude`,
`temperature.time`, `temperature.note`, `bleeding.value`, `bleeding.exclude`, `mucus.feeling`,
`mucus.texture`, `mucus.value`, `mucus.exclude`, `cervix.*`, `note.value`, `desire.value`, `sex.*`,
`pain.*`, `mood.*`. drip writes text in lower case.

| drip. | Mensis |
|---|---|
| `bleeding.value` 0 / 1 / 2 / 3 | spotting / light / medium / heavy |
| `bleeding.exclude` = true | spotting (bleeding drip was told is not menstrual never starts a period) |
| `temperature.value`, `temperature.time` | waking temperature and time; excluded temperatures are not imported, so they cannot affect the temperature-shift rule |
| `mucus.texture` egg white or `mucus.feeling` slippery | egg white |
| `mucus.texture` creamy | creamy |
| `mucus.feeling` wet / dry or nothing | watery / dry |
| `mucus.value` (NFP t, Ø, f, S, S+) | used only when feeling and texture are missing: dry, dry, watery, creamy, egg white |
| `pain.cramps`, `headache`, `migraine`, `backache`, `nausea`, `tenderBreasts` | cramps, headache, headache, back pain, nausea, tender breasts |
| `mood.happy`, `sad`, `stressed`, `anxious`, `balanced`, `fine`, `energetic`, `angry` | happy, low, anxious, anxious, calm, calm, energetic, irritable |
| `mood.fatigue` | symptom fatigue |
| `sex.partner` with any contraceptive flag / without | protected / unprotected |
| `note.value`, `temperature.note`, `pain.note`, `mood.note`, `sex.note` | joined into the day's notes |
| `cervix.*`, `desire.value`, `pain.ovulationPain`, `pain.other`, `mood.other`, solo sex | not tracked by Mensis; counted in the preview |

### Clue (experimental)

Clue does not publish its export format and no sample could be verified, so the Clue importer reads an
assumed structure and is labelled experimental in the app: a JSON array of day objects, or an object
holding that array under `data`, `days` or `entries`. Each day has `day` or `date` (ISO date or
date-time) and optional keys whose values are a string or a list of strings: `period` (spotting, light,
medium, heavy), `pain`, `emotions` or `mood`, `energy`, `digestion`, `skin`, `fluid` or `discharge`,
`sex`, `ovulation_test`, `pregnancy_test`, a number in `bbt` or `temperature` (°C, or °F above 50), and
`notes` or `note`. Anything else is counted as not tracked, never guessed. A synthetic sample of a real
Clue export would let this importer be checked and the label removed.
