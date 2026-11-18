# ScanPro: privacy audit for Google Play

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (HEAD `3692fbf`)
Evidence: source review, CI results and official Google documentation, not devices. No network capture was made, so
what actually leaves a phone is taken from Google's ML Kit disclosure, not measured. Not legal advice.

Related: [Declarations (Data safety draft)](PLAYSTORE_DECLARATIONS.md) · [Permissions](PLAYSTORE_PERMISSIONS_AUDIT.md) ·
[Security](PLAYSTORE_SECURITY_REPORT.md) · [Policy changelog §8, §14](PLAYSTORE_POLICY_CHANGELOG.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

Paths are under `app/src/main/java/com/example/scanpro/` unless they start with `app/` or `docs/`.

## 1. What ScanPro itself does with data

- No account, ads, billing, analytics, crash-reporting service or generative AI: a grep of the build files and sources
  for those SDKs found none (platform audit §8).
- No network code of its own: no `HttpURLConnection`, OkHttp, Retrofit, Ktor or sockets in `app/src/main` or
  `translate/src/main`. The only WebView (`imaging/HtmlToPdf.kt`) has JavaScript off and network loads blocked.
- `usesCleartextTraffic="false"`.

## 2. Data inventory (on the device)

| Data | Where | Protection | Leaves the phone? | Retention / deletion |
|---|---|---|---|---|
| Scanned page photos (originals and processed), PDFs | `filesDir/documents/` (`data/DocumentStore.kt:51`) | App-private storage; optional app lock and locked folders are UI gates, not encryption | Only when the user shares, exports, prints or saves a copy | Until deleted; deleted items go to the Recycle bin (7 days by default, 14 or 30 in Settings), then removed |
| Document metadata, OCR text, notes, tags | Room database (`data/DocumentDatabase.kt`) | App-private | No (unless exported by the user) | Deleted with the document; all on uninstall or clear data |
| ID card scans | Same as documents | Same | Same | Same. Sensitive: owner may wish to mention in the listing that they stay on the phone |
| QR/barcode history (raw payload, may include Wi-Fi passwords and UPI IDs) | `filesDir/scan_history.json` (`data/ScanHistoryStore.kt`) | AES-GCM with a non-exportable Android Keystore key; temp file then rename; capped at 200 entries | No. CSV export strips Wi-Fi passwords and neutralises formulas (`data/ScanHistoryCsv.kt`) | "Save scan history" can be turned off; "Clear all"; excluded from cloud backup and device transfer |
| Saved signatures (PNG) | `filesDir/saved_signatures` (`data/SignatureLibrary.kt:20`) | App-private, plain PNG | No (goes with device transfer) | User deletes in Sign PDF |
| PDF tool results | `filesDir/tool_results` (`data/ToolResultsStore.kt:80`) | App-private | Only when shared | User deletes in "Your results" |
| Recent PDFs from other apps | Persisted read grants; copies up to 30 MB in `recent_pdfs` (`data/RecentPdfStore.kt`) | App-private | No | Removed when taken off Recent |
| Share copies | `cacheDir/exports` | App-private cache, unprotected copies | Handed to the app the user picks | Swept after 24 h (`work/CacheCleaner.kt`) |
| Tool temporaries (incl. unredacted inputs) | `cacheDir` with prefixes `redact_in_`, `hf_in_`, `nup_in_`, `img_preview_`, … | App-private cache | No | Deleted in `finally`; swept on start (`9a3efe5`) |
| Crash reports | `filesDir/crash-reports/` (last 5; `core/crash/CrashReporter.kt`) | App-private | Only if the user sends a report by e-mail/share | Oldest dropped after 5 |
| Edit journal, scanner session | `filesDir/edit_journal`, `filesDir/pro_scanner_session` | App-private | No | Cleared when work completes |
| Settings | DataStore under `files/` | App-private | No | Clear data / uninstall |
| Files in shared storage (All files access) | Read in place via MediaStore (`feature/phonepdfs/PhonePdfSource.kt`) | Read only | No | Not stored, except when the user adds a file to ScanPro |
| Camera frames | Memory only | n/a | No | Discarded |

Backups: `android:allowBackup="true"` but `res/xml/data_extraction_rules.xml` and `backup_rules.xml` exclude the `file`,
`database` and `sharedpref` domains from cloud backup, so nothing goes to Google Drive backup. Device-to-device transfer is
allowed (owner's choice) except `scan_history.json`, `pro_scanner_session/` and `edit_journal/`. User-made backups are
password-encrypted files (`backup/EncryptedDocumentPackage.kt`; zip-slip guarded).

## 3. Data that leaves the phone without a user share

| Flow | Who sends | What | Source | Verified? |
|---|---|---|---|---|
| ML Kit diagnostics | Google ML Kit SDK (text recognition, barcode, language ID, translate, document scanner) | Device info (maker, model, OS, build), app package and version, per-installation identifier (and device identifier for Play-services "thin" features), latency, API configuration, input/output size, feature version, event type, error codes; translate: chosen languages, Firebase installations / remote config | [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) | Disclosure VERIFIED; no documented switch to turn it off; traffic NOT measured |
| Model downloads | Google Play services (OCR scripts, barcode, document scanner) and ML Kit Translate (language packs, any network: `MlKitTranslator.kt:42`) | Download requests | Code | Code read |
| On-demand translation module | Google Play (`intelligence/TranslationFeature.kt`, `SplitInstallManager`) | Play request | Code | Code read |
| In-app review | Google Play In-App Review (`libs.play.review.ktx`) | Play request | `app/build.gradle.kts` | Code read |

Images, scans and recognised text are not part of the ML Kit disclosure list.

## 4. User-initiated sends

- "Send feedback" / "Report an issue" (`core/ui/Support.kt`) open the e-mail app with app version, phone model, Android
  version, screen and memory details, recent app events and the last crash; paths and `content://` URIs are redacted
  (`core/diagnostics/AppDiagnostics.kt`). Exception messages are not redacted and may hold a file title (S22). Nothing is
  sent until the user presses Send.
- The crash prompt (`core/crash/CrashReportPrompt.kt`) can share a report with any app the user chooses.
- Shares, prints and "Open with" use FileProvider read grants for one file (`res/xml/file_paths.xml`; no root or external paths).

## 5. Privacy policy gaps (`docs/privacy-policy/index.html`)

| Gap | Status |
|---|---|
| 9 placeholders (`class="fill"`) and the draft box: effective date, legal name (×2), contact confirmation (×2), hosted URL, children statement, postal address | **FAIL**, owner |
| Not hosted at a public HTTPS URL; no URL passed to the build | **FAIL**, owner |
| All files access, network uses, ML Kit diagnostics, notifications | PASS (corrected in `123b048`) |
| Contact address differs from the app's support address (`core/ui/Support.kt:23`) | **FAIL** (PS-08), owner |
| Drink-water reminders described as "if that reminder is on" while the default is on (`data/SettingsStore.kt:276`) | P2 wording or default change |
| Children section must match the Play target-audience answer | Owner |
| In-app strings "nothing uploaded" contradict §4 of the policy (PS-07) | **FAIL**, wording |
| Data retention and deletion: §6, §9, §15 cover on-device retention and that no server copy exists | PASS (content) |

## 6. Data safety form: summary of the draft

ScanPro's own code collects nothing; ML Kit collects diagnostics. Draft answers (full form in
[Declarations §1](PLAYSTORE_DECLARATIONS.md)): data collected **yes**; shared **no**; encrypted in transit **yes**
(ML Kit uses HTTPS); types **App info and performance → Diagnostics / Other app performance data** and **Device or other
IDs**; purpose **Analytics** (plus App functionality if the owner reads "diagnostics" that way); collection is not
optional (no switch). Photos, files and documents, OCR text, financial info (UPI IDs) and contacts are processed on the
device only and are not "collected" under Play's definition. These are our readings: NOT VERIFIED by Google.

## 7. Actions

1. Owner fills and hosts the policy; passes the URL to `bundleRelease` (PS-01).
2. Owner picks one support address for policy, listing and app (PS-08).
3. Reword the four "nothing uploaded" strings in six languages (PS-07).
4. Optional: a network capture on a device (e.g. PCAPdroid) during scan, OCR, QR and translate to confirm §3.
5. File Data safety from the draft and keep it in step with the policy.
