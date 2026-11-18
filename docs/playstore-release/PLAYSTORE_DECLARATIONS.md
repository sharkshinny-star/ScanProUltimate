# ScanPro: Play Console declarations (draft answers)

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: source review, the official pages in the [policy changelog](PLAYSTORE_POLICY_CHANGELOG.md), not devices and not
the Play Console (no access). **Nothing here has been filed.** Answers marked **OWNER** or **LEGAL** need the owner's
decision or legal advice; answers marked NOT VERIFIED are our reading of Google's rules, not Google's words.
Console question wording may differ from the summaries below.

Related: [Privacy](PLAYSTORE_PRIVACY_AUDIT.md) · [Permissions](PLAYSTORE_PERMISSIONS_AUDIT.md) ·
[Manual actions](PLAYSTORE_MANUAL_ACTIONS.md) · [Runbook](PLAYSTORE_RELEASE_RUNBOOK.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

All are under **Play Console > (app) > Policy and programmes > App content** unless noted.

## 1. Data safety

Basis: ScanPro's own code sends nothing; Google ML Kit (bundled in the app) sends diagnostics
([ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)); Play's form must include SDK
collection ([Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)).

| Question | Draft answer | Note |
|---|---|---|
| Does the app collect or share any required user data types? | **Yes** (collected by ML Kit) | |
| Is all user data encrypted in transit? | **Yes** | ML Kit: "encrypts the data in transit using HTTPS"; the app itself transmits nothing |
| Do you provide a way for users to request deletion? | **No** (no account, nothing held by the developer; Google holds ML Kit diagnostics) | **OWNER**; NOT VERIFIED which answer fits best |
| App info and performance → Diagnostics | Collected; not shared; processed ephemerally: No; **required** (no switch); purpose **Analytics** | NOT VERIFIED whether to add "App functionality" |
| App info and performance → Other app performance data | Collected (latency, configuration, input/output size); same answers | The privacy policy already names this |
| Device or other IDs | Collected (per-installation identifier; device identifier for Play-services features); same answers | |
| App activity → Other actions | Optional: ML Kit "event type". Add only if the owner reads it as app activity | **OWNER** |
| Photos and videos, Files and docs | **Not collected**: processed on the device only ("On-device access/processing … does not need to be disclosed") | Applies to camera, imports and All files access |
| Personal info, Financial info (UPI IDs in QR codes), Contacts, Location, Messages, Audio, Health, Web browsing | **Not collected** | |
| Crash logs | **Not collected** by the app automatically. A crash report leaves the phone only when the user e-mails it | **OWNER**: some developers declare user-sent support e-mail data as "Crash logs / Diagnostics, optional". Conservative choice: declare it as optional |
| Translate (`:translate` module): Firebase installations and remote config, chosen languages | Covered by Device or other IDs and Other app performance data | NOT VERIFIED |
| Independent security review | No | |

Keep this identical to `docs/privacy-policy/index.html` §4 and §12.

## 2. Ads

**No, my app does not contain ads.** Evidence: no ads SDK in `app/build.gradle.kts` or `gradle/libs.versions.toml` (grep).

## 3. App access

**All functionality is available without special access.** No login. Note for reviewers: "Files on this phone" needs the
user to grant All files access on Android 11+ (default build); Translate downloads a module on first use; the app lock
is off by default.

## 4. Target audience and content

| Question | Draft | Status |
|---|---|---|
| Target age groups | 18 and over (or 13+); not designed for children | **OWNER**. Must match the policy's children section (§14 placeholder). Choosing under-13 groups brings the Families policy |
| Appeals to children? | No (document scanner and PDF tools) | **OWNER** |

## 5. Foreground service (dataSync)

Source: [FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821). Service:
`core.operations.OperationForegroundService`, `foregroundServiceType="dataSync"`.

- **Use case:** Data sync → **Local processing: Other** (user-initiated work on files on the device: PDF compression,
  merging, OCR of a document, exports). Alternative: "Local processing: Import or export" for exports.
- **Description:** "When the user starts a long task on their own document (for example compressing or merging PDFs,
  recognising the text of a multi-page scan, or exporting), ScanPro keeps it running if they switch to another app and
  shows a notification with the task's progress. The work is done on the phone; nothing is uploaded."
- **Impact if deferred:** "The task the user just started would not finish until they come back; a half-written output
  file could be lost."
- **Impact if interrupted:** "The output file would be incomplete and the user would have to start again; queued edits
  are journalled and reported, not lost silently."
- **Demo video script (30–60 s, release build, screen recording):** (1) open Tools > Compress PDF; (2) pick a large PDF;
  (3) tap Compress; (4) press Home; (5) pull down the shade: the ScanPro progress notification; (6) return: the result
  screen with size before/after. Upload as an unlisted YouTube video. Status: **not recorded**.
- Google recommends user-initiated data transfer jobs only for **network** transfers; ScanPro's work is local, so dataSync
  fits Google's listed uses (VERIFIED from the page). Long-term option: WorkManager or `mediaProcessing`.

## 6. All files access (`MANAGE_EXTERNAL_STORAGE`) — only if the default build is uploaded

Source: [Use of All files access](https://support.google.com/googleplay/android-developer/answer/10467955). Based on the root
`PLAY_ALL_FILES_ACCESS.md`, corrected for two stale lines there (PS-12). **OWNER** decides whether to file this or to
upload the build without the permission ([Permissions §3](PLAYSTORE_PERMISSIONS_AUDIT.md)).

- **Permitted use:** Document management apps.
- **Core functionality:** "ScanPro is a document scanner and PDF toolkit. Its Files on this phone section finds every PDF,
  Word, Excel, PowerPoint, text file and image on the phone, wherever it was saved (Downloads, chat apps' document
  folders, other apps' export folders), so the user can open it in the built-in viewer and run the PDF tools on it
  (merge, compress, sign, convert, organise pages)."
- **Why SAF / MediaStore are not enough:** "On Android 11 and later, `ACTION_OPEN_DOCUMENT_TREE` cannot grant the storage
  root or the Download folder, and MediaStore does not return other apps' PDF and Office files. A picker shows one file at a
  time and cannot keep a library of every document up to date. ScanPro uses the picker for everything else (import, share,
  Open with)."
- **Data handling:** "Files are only read, on the device. No file name, path or content leaves the phone. The app has no
  account and no server; Google ML Kit diagnostics contain no file data."
- **When asked:** "At first start, on the welcome permission page next to camera and notifications, with an explanation;
  the user can choose Not now and the rest of the app works. The Files section explains it again." (Accurate for F196.)
- **Video script:** fresh install → welcome permission page explaining Files on your phone → Allow → system All files
  access page, switch on → Files list with filters and search → open a PDF → long-press two PDFs, Merge → system settings,
  switch off → app still works. Status: **not recorded**.

## 7. Financial features

Source: [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271) (all apps must complete it).
**Draft: "My app doesn't provide any financial features."** ScanPro creates QR codes for the user's own UPI ID and hands a
scanned `upi://` code to a UPI app the user chooses; it holds no funds, starts no payment and stores no payment
credentials. **NOT VERIFIED interpretation; LEGAL/OWNER confirm.** If a reviewer disagrees, the nearest category is
"Mobile payments and digital wallets".

## 8. Health apps

Source: [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291).
**"My app doesn't provide any health features."** The drink-water reminder is a simple notification, not a health
feature in our reading (NOT VERIFIED; **OWNER** confirms, or turn the reminder off by default: PS-15).

## 9. Content rating (IARC questionnaire hints)

Category: utility / productivity (reference, news or educational is not a fit). Expected answers: no violence, sexual
content, profanity, drugs, gambling or simulated gambling; no user-to-user communication or user-generated content shared
with others; no sharing of the user's location; no digital purchases. The app can open web links from scanned QR codes in
the user's browser (after a preview) and has no in-app browser. **OWNER** answers the live questionnaire.

## 10. Other declarations

| Declaration | Draft | Status |
|---|---|---|
| Account deletion | Not applicable: no accounts | — |
| News app | No | — |
| Government app | No | — |
| COVID-19 contact tracing / status | No | — |
| Generative AI | No generative AI features (dependency grep) | — |
| Advertising ID | Not used (no ads or analytics SDK); confirm the merged manifest has no `AD_ID` permission | NOT VERIFIED until the merged manifest is read |
| Trader status (EEA/UK) | **OWNER/LEGAL**: trader or non-trader. A trader's address and phone become public from 1 Mar 2027 | Required for new accounts at creation; existing accounts by 1 Feb 2027 |
| Privacy policy URL | Hosted `docs/privacy-policy/index.html` | **Not hosted** (PS-01) |
| Developer verification / app registration | Creating the app registers `com.scanpro.app` | **OWNER** |
