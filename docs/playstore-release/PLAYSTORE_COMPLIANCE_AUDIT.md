# ScanPro: Google Play compliance audit

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (HEAD `3692fbf`) · App: `com.scanpro.app` 4.2.0 (versionCode 7)
Evidence: source review, GitHub Actions results and official Google documentation. No phone, emulator, Play Console,
signing key or hosted privacy policy was available, so nothing was checked on a device or in the Console. Not legal advice.

Related: [Policy changelog](PLAYSTORE_POLICY_CHANGELOG.md) (sources and quotes) · [Fixes](PLAYSTORE_FIXES.md) ·
[Permissions](PLAYSTORE_PERMISSIONS_AUDIT.md) · [Privacy](PLAYSTORE_PRIVACY_AUDIT.md) · [Security](PLAYSTORE_SECURITY_REPORT.md) ·
[Declarations](PLAYSTORE_DECLARATIONS.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

Status words: **PASS** (shown by code, CI or a document), **FAIL** (known not met), **NOT APPLICABLE**, **NOT VERIFIED**
(cannot be shown without a device, a build that has not run yet, or the Play Console). Source links marked *(Console page,
not fetched)* name the Play Console screen only; their wording was not checked in this session.

## 1. Policy requirements

| # | Requirement | Official source | Effective date | Applicable? | Current status | Required action |
|---|---|---|---|---|---|---|
| 1 | Target API 36 or higher for new apps and updates | [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878) | 31 Aug 2026 | Yes | **PASS**: `targetSdk = 37` (`app/build.gradle.kts`) | None |
| 2 | Android 16/17 behaviour changes for target 36/37 (edge-to-edge, predictive back, large-screen orientation and resizability ignored, native DCL read-only, CT) | [Android 16](https://developer.android.com/about/versions/16/behavior-changes-16), [Android 17](https://developer.android.com/about/versions/17/behavior-changes-17) | On targeting 36/37 | Yes | **NOT VERIFIED** (code review only; R2) | Device pass on Android 17, a tablet and a foldable ([Compatibility](PLAYSTORE_COMPATIBILITY_REPORT.md)) |
| 3 | Android developer verification: app registered to the account | [Developer verification](https://developer.android.com/developer-verification), [Policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780) | 30 Sep 2026 (registration); global 2027 | Yes | **NOT VERIFIED** (no Console access) | Owner: create the app / confirm registration on Console Home |
| 4 | All files access (`MANAGE_EXTERNAL_STORAGE`): permitted use and approved declaration | [Use of All files access](https://support.google.com/googleplay/android-developer/answer/10467955), [Policy announcement 29 Oct 2026](https://support.google.com/googleplay/android-developer/answer/18100766) | In force; minimum-scope clarification 29 Oct 2026 | Yes (default build) | **FAIL**: declared in the manifest, asked at first launch (F196), declaration not filed | Owner decides: file the declaration, or upload the build without the permission (`-Pscanpro.allFilesAccess=false`). See PS-02 |
| 5 | Foreground service type declaration (`dataSync`) with description and video | [FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821) | Target 34+ | Yes | **FAIL** (not filed) | Owner files it ([Declarations §5](PLAYSTORE_DECLARATIONS.md)) |
| 6 | FGS 6 h / 24 h `dataSync` timeout handled | [FGS timeout](https://developer.android.com/develop/background-work/services/fgs/timeout) | Target 35+ | Yes | **PASS (code)**: `onTimeout` stops the service; `startForeground` guarded (`123b048`). Behaviour NOT VERIFIED on a device | Device test with a long job in the background |
| 7 | Data safety form covers SDK collection (ML Kit diagnostics) | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) | In force | Yes | **FAIL** (not filed; draft ready) | Owner files [Declarations §1](PLAYSTORE_DECLARATIONS.md) |
| 8 | Privacy policy: public non-PDF URL in Console and link in the app; developer identity and contact | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) | In force | Yes | **FAIL**: `docs/privacy-policy/index.html` has 9 `class="fill"` placeholders and a draft box; not hosted. In-app link: **PASS (gate)** `bundleRelease` fails without `-Pscanpro.privacyPolicyUrl=https://…` | Owner fills, hosts and passes the URL (PS-01) |
| 9 | App claims match behaviour (Deceptive Behaviour / metadata) | [Metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842) | In force | Yes | **FAIL (minor)**: four UI strings say "nothing uploaded" while ML Kit sends diagnostics (PS-07) | Reword strings; listing text drafted accurately ([Store listing](PLAYSTORE_STORE_LISTING.md)) |
| 10 | Financial features declaration (every app) | [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271) | Since 31 Aug 2023 | Yes (form) | **FAIL** (not filed). Answer "no financial features" is our reading for a UPI QR generator: NOT VERIFIED | Owner files; legal check of the UPI reading |
| 11 | Health apps declaration (every app) | [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291) | Since 31 Aug 2024 | Yes (form) | **FAIL** (not filed) | Owner files "no health features" |
| 12 | Trader status (EEA/UK) | [Trader status declaration](https://support.google.com/googleplay/android-developer/answer/17594752) | New accounts at creation; existing by 1 Feb 2027 | Yes | **NOT VERIFIED** (owner's legal decision) | Owner declares |
| 13 | Closed test 12 testers × 14 days (personal accounts created after 13 Nov 2023) | [App testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) | In force | Yes if personal account | **NOT VERIFIED** (account type unknown) | Owner runs the closed test if it applies |
| 14 | Target audience and content; content rating questionnaire; ads declaration; app access | Play Console > App content *(Console page, not fetched)* | In force | Yes | **FAIL** (not filed; drafts in [Declarations](PLAYSTORE_DECLARATIONS.md)) | Owner files |
| 15 | Account deletion | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) | In force | **NOT APPLICABLE**: no accounts (grep of build files for sign-in SDKs: none) | NOT APPLICABLE | None |
| 16 | Store listing limits and metadata (name 30, short 80, full 4,000; no ranking claims; assets to spec) | [Store listing](https://support.google.com/googleplay/android-developer/answer/9859152), [Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151) | In force | Yes | **PASS (text)**: 28 / 71 / 2,947 characters drafted. Assets: **FAIL** (none captured) | Owner captures screenshots, makes icon and feature graphic |
| 17 | 16 KB page size for native code | [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) | Treat as required now (pages disagree: 1 Nov 2025 vs 1 Feb 2027) | Yes (OpenCV, `libc++_shared.so`) | Committed `.so`: **PASS** (64-bit LOAD align 0x4000, platform audit). Packaged AAB: **NOT VERIFIED** (release-check not run) | Run the Release check workflow (PS-09) |
| 18 | App Bundle and Play App Signing; base module ≤ 500 MB | [About app bundles](https://support.google.com/googleplay/android-developer/answer/9844279), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756), [Size limits](https://support.google.com/googleplay/android-developer/answer/9859372) | Since Aug 2021 | Yes | AAB build: **PASS (config)**. Signing: **FAIL** (no key; unsigned without `SCANPRO_KEYSTORE`) | Owner creates upload key, enrols in Play App Signing (PS-03) |
| 19 | Memory and bitmap thresholds; DEX optimisation 25 % | [Technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799), [Android vitals](https://developer.android.com/topic/performance/vitals) | Feb 2027 | Yes | **NOT VERIFIED** (no device, no vitals). R8 and resource shrinking on: **PASS (config)** | Performance plan in [Test report §5](PLAYSTORE_TEST_REPORT.md) |
| 20 | Core vitals: crash 1.09 %, ANR 0.47 %, per model 8 %, wake locks 5 % | [Android vitals](https://developer.android.com/topic/performance/vitals) | Current | Yes | **NOT VERIFIED** (no installs) | Watch after launch ([Runbook](PLAYSTORE_RELEASE_RUNBOOK.md)) |
| 21 | Play Billing Library 8+ | [PBL deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq) | 31 Aug 2026 | No billing dependency | **NOT APPLICABLE** | None |
| 22 | Local Network, Contacts, Location, SMS/Call log, Photo and video permissions | [Policy announcement 29 Oct 2026](https://support.google.com/googleplay/android-developer/answer/18100766) | Various 2027 | No such permissions requested (manifest) | **NOT APPLICABLE** | Do not add them |
| 23 | Ads, generative AI, user-generated content, news, government | Play Console > App content *(Console page, not fetched)* | In force | None in the app (dependency grep: no ads, analytics, AI or account SDKs) | **NOT APPLICABLE** (answer "no" in Console) | Owner answers "no" |

## 2. Findings

Severity: **P0** blocks a Play release, **P1** must be done before release, **P2** improvement. IDs PS-* are this
folder's; A* and S* refer to the platform and security audits of 2026-11-18 summarised in [Fixes](PLAYSTORE_FIXES.md) and
[Security](PLAYSTORE_SECURITY_REPORT.md).

### P0

**PS-01 Privacy policy is an unfilled draft and not hosted (A10).**
- Files: `docs/privacy-policy/index.html`; `app/src/main/java/com/example/scanpro/core/ui/Support.kt:24` (`PRIVACY_POLICY_URL = ""`).
- Evidence: 9 `class="fill"` placeholders (date, legal name ×2, address confirmation ×2, hosted URL, children, postal
  address) and a "Before you publish" box. Content was corrected in `123b048` (All files access, network uses,
  notifications); legal fields remain.
- Impact: Play requires a public policy in the Console and in the app; the bundle gate refuses an upload build without an `https://` URL.
- Fix: owner fills the fields, removes the draft box, hosts the page, passes the URL to the build ([Manual actions 4](PLAYSTORE_MANUAL_ACTIONS.md)).
- Verification: gate code read (`app/build.gradle.kts`, `tasks.matching { it.name == "bundleRelease" }`). The gate itself has not run in CI yet (pending). Policy: FAIL.

**PS-02 All files access asked at start, declaration not filed (A7).**
- Files: `app/src/main/AndroidManifest.xml` (`MANAGE_EXTERNAL_STORAGE`), `ui/WelcomeScreen.kt:128`, `ui/ScanProApp.kt:293`, `ui/FilesAccessStartDialog.kt`, `PLAY_ALL_FILES_ACCESS.md`.
- Evidence: owner approved the permission (2026-11-16) and chose to ask at start (F196, `143bedb`, 2026-11-18). Use is read-only listing of documents in shared storage.
- Impact: Google lists "document management" as a permitted use but its text says "locate, access, and edit", and invalid uses include "file selection activity". Rejection blocks the whole release, not just the feature.
- Fix: either file the declaration (draft in [Declarations §6](PLAYSTORE_DECLARATIONS.md)) and describe the feature in the listing, or upload the `-Pscanpro.allFilesAccess=false` build (`123b048`). That build removes the permission (`app/src/noAllFilesAccess/AndroidManifest.xml`, `tools:node="remove"`) and hides the welcome item and the start prompt (`BuildConfig.ALL_FILES_ACCESS`), **but the Files tab and Home "Files on this phone" access cards (`feature/phonepdfs/PhonePdfsScreen.kt:215, 900`) are not gated** (that code belongs to another session): they would still offer "Allow access" and open a settings page for a permission the app no longer declares. See PS-11.
- Verification: code read. Neither build has been compiled with the switch or run on a device: NOT VERIFIED.

**PS-03 No upload key, no Play App Signing (A3).**
- Files: `app/build.gradle.kts` (`signingConfigs.release` reads `SCANPRO_KEYSTORE*`; otherwise the release build is unsigned and still succeeds).
- Impact: an unsigned AAB cannot be uploaded; mistakes are easy because the build does not fail.
- Fix: owner creates an upload key (RSA ≥ 2048), enrols in Play App Signing, decides the key strategy for the sideload APK.
- Verification: FAIL (no key exists in this environment, correctly; none committed: `.gitignore` covers `*.jks`, `*.keystore`, `local.properties`).

**PS-04 Build of HEAD and release checks not yet proven.**
- Evidence: last green push run on this branch is `44323159144` (docs F177); last green PR run `44353049118` on `a8f220c`. CI run 183 on `3c06b85` compiled (965 tests, 1 failed, premise fixed in `c162957`). Runs on `e8a31d3` and `e764f73` failed to compile (`ToolDemos.kt:378`, fixed in `d2ba7aa`/`3860b76`; `scale` import fixed in `3dcaa66`). Later runs were cancelled by newer pushes; run `44375015645` on `3692fbf` was in progress when this was written.
- The Release check workflow (`.github/workflows/release-check.yml`, `cf6b35e`) has never run. `gh run list --workflow release-check.yml` answers "workflow … not found on the default branch": GitHub lists and dispatches `workflow_dispatch` workflows from the default branch, so it can probably only be started once the file is on `main` (NOT VERIFIED whether dispatch by workflow ID with `--ref` works).
- Impact: F193, F195–F197, F210–F212 compile and test status: pending CI. 16 KB alignment of the packaged bundle unknown.
- Fix: wait for CI on `3692fbf`; get the release-check workflow onto the default branch or run `scripts/ci/release-check.sh` locally after `bundleRelease`.

**PS-05 No device test of a release build (A16; R2, R7, R44, R55, R60, R64, R69, R70, R71).**
- Impact: camera, OCR, PDF tools, All files access, FGS and Android 17 large-screen behaviour unproven. A scanner that fails on camera start would breach the crash/ANR thresholds.
- Fix: run the [manual test plan](PLAYSTORE_TEST_REPORT.md) on a signed release build. Verification: NOT VERIFIED.

**PS-06 Play Console declarations not filed.** Data safety, FGS, All files access (if kept), financial features, health,
content rating, target audience, ads, app access, trader status. Drafts: [Declarations](PLAYSTORE_DECLARATIONS.md). Verification: FAIL (owner action).

### P1

**PS-07 "Nothing uploaded" strings versus ML Kit diagnostics (S23).** `app/src/main/res/values/*.xml`:
`lib_privacy_line`, `settings_privacy_subtitle`, `settings_footer_privacy`, `ui_welcome_tagline` say "nothing uploaded"
(and the translations). ML Kit sends diagnostics to Google ([ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)).
Fix: "Your documents never leave your phone" (owner wording) in six languages. Not done. Verification: FAIL.

**PS-08 Support contact mismatch (A10).** The policy and `PLAY_ALL_FILES_ACCESS.md` use `support@scanpro.app`;
`core/ui/Support.kt:23` `SupportLinks.EMAIL` is a different, personal address. The Play listing contact, the policy and the
app must agree. Owner decision. FAIL.

**PS-09 16 KB packaging and `libc++_shared.so` choice unverified (A14).** `app/build.gradle.kts`
`jniLibs { pickFirsts += "**/libc++_shared.so" }`. The six committed OpenCV/libc++ files are aligned (64-bit). Which copy
wins and the alignment of third-party `.so` in the AAB: NOT VERIFIED. Fix: run the release check (bundle `.so` LOAD
alignment, `zipalign -c -P 16` on the sideload APKs, merged manifest). Note the script does not check RELRO and does not
zip-check AAB-derived APKs (needs bundletool).

**PS-10 versionCode 7 (A4).** Must exceed every versionCode already uploaded to any track. NOT VERIFIED (no Console).

**PS-11 Build without All files access is incomplete.** See PS-02: `PhonePdfsScreen.kt` access cards ignore
`BuildConfig.ALL_FILES_ACCESS`. Fix (for the session that owns `feature/phonepdfs`): when the flag is false, replace the
cards with "Add a folder" (SAF tree) or hide "Files on this phone". FAIL until done, if that build is used.

**PS-12 Root `PLAY_ALL_FILES_ACCESS.md` has two stale lines.** "Nothing is uploaded: the app has no network code,
analytics or accounts" (ML Kit sends diagnostics) and "If it is refused, options (not done)" (the switch exists since
`123b048`). Correct before pasting into the Console. FAIL (outside this folder; not edited here).

**PS-13 Redaction fix not yet run (S1–S4, F212).** Code reviewed (`imaging/PdfCropRedactTools.kt`: `importClean`,
`scrubReferences`, catalogue reduced to `/Type` and `/Pages`, `verifyRedacted` fails closed); `EditToolsTest` (12 tests)
pending CI. Residual: the read-back check only matches ASCII terms of 3+ characters; non-Latin terms rely on page
rasterising and the page-tree count. Details in [Security](PLAYSTORE_SECURITY_REPORT.md).

**PS-14 Translations unreviewed.** Hindi, Tamil, Telugu, Kannada and Malayalam strings (e.g. `1244933`, `4f2efe7`) are
machine-made and unreviewed. A wrong permission or privacy sentence in another language is a policy risk. Owner review.

### P2

| ID | Item | Evidence | Fix |
|---|---|---|---|
| PS-15 | Drink-water reminders on by default in a scanner; policy says "if that reminder is on" | `data/SettingsStore.kt:273-276` (`?: true`), `docs/privacy-policy/index.html` §10 | Owner: default off, or say "on by default" in the policy |
| PS-16 | Translation models download on any network | `translate/.../MlKitTranslator.kt:42` `DownloadConditions.Builder().build()` | Ask before mobile-data download |
| PS-17 | FGS starts just to stop; no Cancel action (A13) | `core/operations/OperationRunner.kt`, `OperationForegroundService.kt` | Track whether a show was sent; add a Cancel action |
| PS-18 | Exported activity accepts custom actions from any app (A8, S18) | `MainActivity.kt` `ACTION_REPORT_ISSUE` etc. | Accept only from shortcuts/widget |
| PS-19 | Broad ProGuard keeps and `-dontwarn` (A5) | `app/proguard-rules.pro` | Narrow after a release build plus device tool tests |
| PS-20 | Translate split re-declares ML Kit registrars (A17) | `translate/src/main/AndroidManifest.xml` | Device check with bundletool `--local-testing` |
| PS-21 | Sign PDF breaks an existing digital signature silently (S9) | `imaging/SignPdfEngine.kt` | Warn when `signatureDictionaries` is not empty |
| PS-22 | Protect falls back to AES-128 silently (S10) | `imaging/PdfSecurity.kt:24` | Say so in the result |
| PS-23 | UPI button label prefers the code's own `pn` name (S21) | `scanner/BarcodeResultSheet.kt` | Show the UPI ID and amount first |
| PS-24 | Android CI pushes on a non-main branch, 30 min timeout | `.github/workflows/android.yml` | Allowed by the file's own note (public repo); owner may tighten |
