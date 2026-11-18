# Google Play and Android release requirements for ScanPro (checked 2026-11-18)

Research date: **2026-11-18**. Sources: official Google pages only (support.google.com/googleplay/android-developer,
developer.android.com, android-developers.googleblog.com, developers.google.com/ml-kit). Every page below was fetched on
2026-11-18. Quotes are copied from the page text. "Page date" is the "Last updated" stamp the page shows. Play Help
Centre pages show no such stamp (only "©2026 Google"), and the table says so.

**VERIFIED** means the rule text was read on an official page today. **NOT VERIFIED** means no primary source was found,
the official pages disagree, or the point is our reading of how a rule applies to ScanPro rather than Google's words.
Nothing here is legal advice. Google's reviewers make the final decision on declarations.

ScanPro facts used (from the repo on 2026-11-18): `targetSdk = 37`, `minSdk = 24` (`app/build.gradle.kts`); ML Kit
OCR and barcode use the Google Play services (unbundled) artifacts by default, with bundled ones only under
`-Pscanpro.bundledMl=true`; also `play-services-mlkit-document-scanner`, and ML Kit Translate and language ID in the
`:translate` module; `OperationForegroundService` declares `foregroundServiceType="dataSync"` and overrides
`onTimeout`; `jniLibs { pickFirsts += "**/libc++_shared.so" }`; `System.load()` appears only in a unit test.

---

## Summary table

| # | Requirement | Official source | Effective date | Applies to ScanPro? | Required action | Verified? |
|---|---|---|---|---|---|---|
| 1a | New apps and updates must target API 36 or higher (phones) | [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878) | 31 Aug 2026 (extension possible to 1 Nov 2026) | Yes, already met (target 37) | None; keep target 37 | VERIFIED |
| 1b | Android 17 (API 37) released | [Android 17 is here (blog)](https://android-developers.googleblog.com/2026/06/Android-17.html) | 16 Jun 2026 | Yes | Test the API 37 behaviour changes (1c) | VERIFIED (the post doesn't use the word "stable") |
| 1c | API 36/37 behaviour changes: edge-to-edge opt-out gone, predictive back on, large-screen orientation and resizability ignored (no opt-out at 37), native DCL read-only, CT on by default, local network blocked by default | [Android 16](https://developer.android.com/about/versions/16/behavior-changes-16), [Android 17](https://developer.android.com/about/versions/17/behavior-changes-17) | On targeting 36 / 37 | Yes | Check on a tablet or foldable that UI works in every orientation and size; keep edge-to-edge and predictive back | VERIFIED |
| 2 | Android developer verification: Play apps must be registered in Play Console | [Policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780), [developer verification](https://developer.android.com/developer-verification) | 30 Sep 2026 (registration); regional protections 30 Sep 2026; global 2027 | Yes | Check Play Console Home that `com.scanpro.app` is registered; also register the sideload build if it is shipped outside Play | VERIFIED |
| 3a | Local Network permission policy (`ACCESS_LOCAL_NETWORK`) | [Policy announcement 29 Oct 2026](https://support.google.com/googleplay/android-developer/answer/18100766), [Policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780) | 25 Aug 2027 | No (ScanPro doesn't request it) | Don't add `ACCESS_LOCAL_NETWORK` | VERIFIED |
| 3b | Contacts (`READ_CONTACTS` 27 Jan 2027) and Manage Contacts (25 Aug 2027) | same | 27 Jan 2027 / 25 Aug 2027 | No | None | VERIFIED |
| 3c | All Files Access: "reiterated minimum scope principles" | [Policy announcement 29 Oct 2026](https://support.google.com/googleplay/android-developer/answer/18100766) | 29 Oct 2026 (clarification, enforcement unchanged) | **Yes** | See 6 | VERIFIED |
| 3d | Trader status declaration (EEA/UK) | [Trader status declaration](https://support.google.com/googleplay/android-developer/answer/17594752) | Existing accounts by 1 Feb 2027; new accounts at creation; public display from 1 Mar 2027 | Yes (every account) | Declare trader or non-trader in Play Console | VERIFIED |
| 4 | 16 KB page size support for apps with native code | [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) | Updates blocked from **1 Feb 2027**. A 2025 blog gave 1 Nov 2025; Play's quality page lists it as a current requirement | **Yes** (OpenCV `.so`, `libc++_shared.so`) | Verify each `.so` LOAD alignment is 2**14 or more, `zipalign -c -P 16`, and RELRO; check the picked `libc++_shared.so` | VERIFIED (rule); date: the pages disagree, so treat it as already required |
| 5a | Memory (anon RSS + swap) and bitmap memory bad-behaviour thresholds | [Play Console technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799), [Android vitals](https://developer.android.com/topic/performance/vitals) | Feb 2027 | Yes (phone/tablet apps) | Keep background/cached bitmaps low; release on `onTrimMemory`; watch vitals | VERIFIED |
| 5b | DEX optimisation 25% (apps with more than 10 MB DEX) | same | Feb 2027 | Unclear (depends on DEX size) | Check DEX size and percentages in App bundle explorer; keep R8 on | VERIFIED |
| 5c | Crash 1.09% / ANR 0.47% overall, 8% per phone model; wake locks 5% | [Android vitals](https://developer.android.com/topic/performance/vitals) | Current | Yes | Monitor vitals | VERIFIED |
| 5d | Zero-tap sign-in restoration | [Play Console technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799) | Apr 2027 | No (no sign-in) | None | VERIFIED |
| 6 | `MANAGE_EXTERNAL_STORAGE` permitted uses and declaration | [Use of All files access permission](https://support.google.com/googleplay/android-developer/answer/10467955) | In force | **Yes, high risk** | Permissions Declaration Form with an SAF/MediaStore justification, or a Play build without the permission | VERIFIED (rule); approval outcome NOT VERIFIED |
| 7 | FGS type declaration with demo video; dataSync 6 h / 24 h timeout | [FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821), [FGS timeout](https://developer.android.com/develop/background-work/services/fgs/timeout) | In force (A14 declaration; A15 timeout) | Yes | Declare `dataSync` as "Local processing: Other" (or Import/export) with a video; keep `onTimeout` → `stopSelf()` | VERIFIED |
| 8 | Data safety must include SDK collection; ML Kit collects diagnostics | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) | In force | **Yes** | Declare ML Kit diagnostics (device/app info, identifiers, performance) | VERIFIED (disclosure); opt-out NOT VERIFIED (none documented) |
| 9 | Financial features declaration (all apps) | [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271) | Since 31 Aug 2023 | Yes (form required) | Probably "My app doesn't provide any financial features" | Form VERIFIED; answer for UPI QR NOT VERIFIED |
| 10 | Health apps declaration (all apps) | [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291) | Since 31 Aug 2024 | Yes (form required) | "My app doesn't provide any health features" | VERIFIED |
| 11 | Personal accounts: closed test with 12 testers for 14 days | [App testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) | Accounts created after 13 Nov 2023 | Yes if the account is personal and new | Run the closed test before applying for production | VERIFIED |
| 12 | Listing limits: name 30, short 80, full 4,000; icon 512×512; feature graphic 1024×500; 2 to 8 screenshots | [Store listing](https://support.google.com/googleplay/android-developer/answer/9859152), [Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151), [Metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842) | In force | Yes | Prepare assets to spec; no "#1", "best" or "free" claims | VERIFIED |
| 13 | AAB required for new apps; Play App Signing; base module 500 MB | [About app bundles](https://support.google.com/googleplay/android-developer/answer/9844279), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756), [Size limits](https://support.google.com/googleplay/android-developer/answer/9859372) | Since Aug 2021 | Yes | Upload AAB; enrol in Play App Signing; decide on the key strategy for the sideload APK | VERIFIED |
| 14 | Privacy policy in Console and in app; public non-PDF URL | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) | In force | Yes | Host `docs/privacy-policy` publicly; add an in-app link; name ML Kit data | VERIFIED |
| 15 | Play Billing Library version 8+ for new apps and updates | [PBL deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq) | 31 Aug 2026 (extension to 1 Nov 2026) | No (no billing; no digital purchases) | None while there are no in-app purchases | VERIFIED |

---

## 1. Target API level, Android 16/17 status and behaviour changes

**Target API rule.** [Target API level requirements for Google Play apps](https://support.google.com/googleplay/android-developer/answer/11926878)
(page date: not shown).

> "Starting August 31, 2026: New apps and app updates must target Android 16 (API level 36) or higher to be submitted to
> Google Play; except for Wear OS, and Android Automotive OS apps, which must target Android 15 (API level 35) or higher,
> and Android TV and Android XR apps, which must target Android 14 (API level 34) or higher."

> "You will be able to request an extension to November 1, 2026 if you need more time to update your app."

> "Existing apps must target Android 15 (API level 35) or higher to remain available to new users on devices running
> Android OS higher than your app's target API level."

- 2027 target requirement: **NOT VERIFIED**. The page doesn't announce one.
- **Applies:** yes, and already met. API 37 meets "API level 36 or higher".

**Android 17 status.** [Android 17 is here](https://android-developers.googleblog.com/2026/06/Android-17.html) (post
dated 16 June 2026):

> "Today we're releasing Android 17 and making it available on most supported Pixel devices."

The Android 17 release notes (page date 2026-11-18) list Beta 1 to Beta 4.1 (Beta 4.1: 1 June 2026) plus QPR1 and QPR2
betas. Note: neither page uses the word "stable", and no page found says in so many words that Play accepts
`targetSdk 37`. Play's rule ("36 or higher") allows it. **VERIFIED** as released; explicit Play wording NOT VERIFIED.

**Android 16 changes for apps targeting 36** ([behaviour changes, Android 16](https://developer.android.com/about/versions/16/behavior-changes-16),
page date 2026-10-28):

- Edge-to-edge: "For apps targeting Android 16 (API level 36), `R.attr#windowOptOutEdgeToEdgeEnforcement` is deprecated
  and disabled, and your app can't opt-out of going edge-to-edge."
- Predictive back: "the predictive back system animations (back-to-home, cross-task, and cross-activity) are enabled by
  default. Additionally, `onBackPressed` is not called and `KeyEvent.KEYCODE_BACK` is not dispatched anymore."
  ScanPro already sets `android:enableOnBackInvokedCallback="true"`.
- Large screens: "orientation, resizability, and aspect ratio restrictions no longer apply on displays with smallest
  width >= 600dp." The temporary opt-out (`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`) "won't apply when targeting
  API level 37".
- Local network: opt-in only in Android 16, enforced later (see Android 17).

**Android 17 changes for apps targeting 37** ([behaviour changes, Android 17](https://developer.android.com/about/versions/17/behavior-changes-17),
page date 2026-11-11):

- Large screens: "Developers have the option to opt out of these changes with SDK 36, but this opt-out will no longer be
  available for apps that target Android 17 (API level 37) or higher." **Affects ScanPro.** Camera and editor screens
  must work in landscape and resized windows on tablets and foldables.
- Native DCL: "All native files loaded using `System.load()` must be marked as read-only. Otherwise, the system throws
  `UnsatisfiedLinkError`." ScanPro (OpenCV) uses `System.loadLibrary` from the APK, and `System.load()` only in a unit
  test, so it **probably isn't affected**. That is our reading, NOT VERIFIED on a device.
- Certificate transparency: "If an app targets Android 17 (API level 37) or higher, certificate transparency (CT) is
  enabled by default." ScanPro makes almost no network calls, so the impact is low.
- Local network: "Apps targeting Android 17 (API level 37) or higher now have two paths to maintain communication with
  LAN devices: Adopt system-mediated, privacy-preserving device pickers … or explicitly request this new permission at
  runtime." ScanPro has no LAN use, so it isn't affected.
- Also changed for target 37: the lock-free `MessageQueue` (breaks reflection on its private fields), `static final`
  fields can't be modified through reflection, `setContentCaptureEnabled(false)` no longer works (use `FLAG_SECURE`),
  background audio hardening, the widget `RemoteViews` bitmap memory limit, an SMS OTP 3-hour delay, and BAL hardening
  for `IntentSender`.
- Foreground services: no new FGS-type change for target 37 was found on this page. The background-audio rule needs an
  FGS for background audio, which ScanPro doesn't do.

**ScanPro must:** check every screen at sw ≥ 600 dp in landscape and in split screen, with no locked orientation (on a
device: unverified in the cloud), and keep edge-to-edge insets correct.

## 2. Android developer verification

[Android developer verification](https://developer.android.com/developer-verification) (page date: not shown):

> "**September 30, 2026** Protections begin for all users who install apps from participating stores in Brazil,
> Indonesia, Singapore, and Thailand on certified devices running Android 8 (API level 26) or higher."
> "**2027 and beyond** Verification capability will be expanded globally for all apps on certified Android devices in 2027."

[Google Play Console path](https://developer.android.com/developer-verification/guides/google-play-console):

> "For most Play developers, **no new action** is required for identity verification."
> "Play developers must register their app package names, which may include proving ownership of the private signing
> keys used for those packages." "For new apps, when you create an app in the Google Play Console, Google Play
> automatically registers the package name and links it to your account."
> "You can also use the Play Console to register apps you distribute outside of Google Play to ensure they remain
> installable on certified Android devices."

[Policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780), 2026-09-30, "Play Console
Requirements" (announced 2026-07-15):

> "To meet Android developer verification requirements and Play Console requirements, you must register your Play apps
> in Play Console. While 99% of apps on Play have been registered automatically, you should check your Play Console Home
> page and register any remaining apps … to avoid global removal from Google Play".

**Applies:** yes. **Must do:** create the app in Play Console (this registers `com.scanpro.app`). If the sideload APK is
shipped outside Play, register it too and make sure its signing key matches what is registered. The global 2027 date has
no exact day published: **NOT VERIFIED**.

## 3. Play policy updates in 2026 and announced 2027 deadlines

[Policy announcement: October 29, 2026](https://support.google.com/googleplay/android-developer/answer/18100766):

> "You'll have at least 30 days from October 29, 2026 to update your app to comply with the new and updated policy changes below."
> "**Local Network Permission**: This permission is restricted to apps that need it for core functionality when device
> pickers are insufficient. Apps targeting Android 17 or higher (API 37+) must submit a Play Console declaration to use
> this permission."
> "**Manage Contacts Permission**: This permission is restricted to apps that need it for core functionality when system
> pickers are insufficient and apps must submit a Play Console declaration to use it."
> "**All Files Access Permission**: We've reiterated minimum scope principles to support developer comprehension of the
> use of the MANAGE_EXTERNAL_STORAGE permission."
> "Starting in November 2026, you must declare your trader status in Play Console in accordance with new Console requirements."
> "We recently announced a February 2027 compliance deadline for new app quality requirements regarding memory and code
> optimization thresholds."

Deadlines in 2027 from the [Policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780) table:

| Deadline | Policy | Applies to ScanPro? |
|---|---|---|
| 2027-01-27 | Contacts (`READ_CONTACTS`, Android Contact Picker) | No |
| 2027-01-27 | Location (location button, minimum scope) | No (no location permission) |
| 2027-01-27 | `READ_CALL_LOG` no longer allowed for account verification | No |
| 2027-01-27 | FGS: geofencing removed as an approved use case | No |
| 2027-01-27 | Subscriptions with eligibility-based discounts | No |
| 2027-02-01 | Trader status declaration ([page](https://support.google.com/googleplay/android-developer/answer/17594752)) | **Yes** |
| Feb 2027 | Memory and DEX optimisation thresholds (section 5) | **Yes** |
| 2027-02-01 | 16 KB updates (developer.android.com, section 4) | **Yes** |
| 2027-04-14 | AppFunctions API policy | No |
| Apr 2027 | Zero-tap sign-in restoration | No (no sign-in) |
| 2027-08-25 | Local Network Permission declaration | No |
| 2027-08-25 | Manage Contacts Permission | No |

[Restricted permissions with minimum scope alternatives](https://support.google.com/googleplay/android-developer/answer/16935362)
gives the timeline for `ACCESS_LOCAL_NETWORK`: Console prompt June 2027, mandatory 25 August 2027, 30-day self-extension.
A search summary suggested a preview page cites API 38 for Manage Contacts. That was not confirmed on a primary page:
**NOT VERIFIED** (and irrelevant to ScanPro).

## 4. 16 KB memory page size

[Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) (page date 2026-11-04):

> "all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on
> Google Play. Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to
> release these updates."

Conflict: the [May 2025 blog](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html)
says "Starting November 1st, 2025, all new apps and updates to existing apps submitted to Google Play and targeting
Android 15+ devices must support 16 KB page sizes." The [Play Console technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799)
list it under current requirements: "Apps that contain native code must support devices with 16 KB memory page sizes."
The exact enforcement date for phone updates is therefore **NOT VERIFIED**. Treat it as required now for any upload.

How to verify (quotes from the developer page):

- ELF: `llvm-objdump -p SHARED_OBJECT_FILE.so | grep LOAD` and "ensure that the load segments don't have values less
  than `2**14`." (`llvm-readelf -Wl` is given for the RELRO check.)
- Zip: `zipalign -v -c -P 16 4 APK_NAME.apk`, where the last line must say "Verification successful". It needs
  build-tools 35.0.0 or later.
- Bundle: `bundletool dump config --bundle=<my .aab> | grep alignment` should show `PAGE_ALIGNMENT_16K`.
- RELRO: compliant if `(VirtAddr + MemSiz) % 0x4000 == 0`, or no `GNU_RELRO`, or `GNU_RELRO` fills its `LOAD` segment.
  This "typically happens if the `.so` file was built with NDK toolchain r27 and lower".
- Tooling: "AGP version 8.5.1 or higher and NDK version r28 or higher … your app is 16 KB compatible by default"; "NDK
  version r28 and higher compile 16 KB-aligned by default."
- `libc++_shared`: "Even if your app dynamically links to the C++ standard library (`libc++_shared.so`) from NDKs r26
  and lower, some of which don't have a 16 KB aligned `libc++_shared.so` …" ScanPro uses `pickFirsts` for
  `libc++_shared.so`, so the copy that wins must be checked.
- `check_elf_alignment.sh` checks `arm64-v8a` (and the page says to fix `x86_64` too). `armeabi-v7a` is 32-bit and the
  rule says "on 64-bit devices".

**Must do:** run the ELF, zipalign and RELRO checks on the release AAB-derived APKs (arm64-v8a and x86_64), including
OpenCV and `libc++_shared.so`.

## 5. Memory, bitmap, DEX and Android vitals thresholds

[Play Console technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799)
(announced 26 August 2026; page date not shown):

> "From February 2027, apps and games on Play will need to meet new bad behavior thresholds."
> "Play uses the last 28 days of data to evaluate your app's quality." The 90th percentile is used.

Memory usage (anonymous RSS + swap), **apps**, 90th percentile (cross-checked with [Android vitals](https://developer.android.com/topic/performance/vitals),
page date 2026-10-19):

| RAM tier (total memory) | Foreground | User-perceived services | Background | Cached |
|---|---|---|---|---|
| 0–4 GB (0–3200 MB) | – | – | – | – |
| 4 GB (3200–4800 MB) | 2.00 GB | 1.00 GB | 1.00 GB | – |
| 6 GB (4800–6800 MB) | 2.25 GB | 1.25 GB | 1.25 GB | – |
| 8 GB (6800–9216 MB) | 2.25 GB | 1.50 GB | 1.50 GB | – |
| 12 GB (9216–14336 MB) | 3.25 GB | 1.75 GB | 1.75 GB | – |
| 16 GB (14336–18432 MB) | 4.25 GB | 2.00 GB | 2.00 GB | – |

Bitmap memory: Foreground "–", user-perceived services **200 MB**, background **200 MB**, cached **400 MB**.

DEX: "You will need to achieve a minimum of 25% optimization, obfuscation and shrinking for any app uploads to Play
Console … this requirement will only be enforced where you have non-negligible DEX sizes", which the page sets at
"Apps with > 10 MB DEX code".

Consequence: "Apps exceeding the thresholds for memory usage, bitmap memory usage, or code optimization may see store
visibility impact starting from February 2027" (Android vitals).

Current core vitals (Android vitals): user-perceived crash rate **1.09%** overall / **8%** per phone model;
user-perceived ANR rate **0.47%** / **8%**; excessive partial wake locks **5%**.

**Applies:** yes. A PDF or scanner app can hold large page bitmaps during a long job in the foreground service, which
counts as the "user-perceived services" state with its 200 MB bitmap limit. That mapping is our reading: NOT VERIFIED.
**Must do:** decode at display size, free bitmaps on `TRIM_MEMORY_UI_HIDDEN`, keep R8 full mode, and check App bundle
explorer for DEX size and the optimisation percentages.

## 6. `MANAGE_EXTERNAL_STORAGE` (All files access): most important

[Use of All files access (MANAGE_EXTERNAL_STORAGE) permission](https://support.google.com/googleplay/android-developer/answer/10467955)
(page date: not shown):

> "This is only applicable to apps that target Android 11 (API level 30) and declare the MANAGE_EXTERNAL_STORAGE
> permission … this policy does not impact the usage of the READ_EXTERNAL_STORAGE permission."
> "Apps that fail to meet policy requirements or do not submit a Permissions Declaration Form may be removed from Google Play."
> "You should only access the All files access permission when your app cannot effectively make use of the more
> privacy-friendly best practices, such as the Storage Access Framework or the MediaStore API."
> "the app's usage of the permission must fall within permitted uses and must be directly tied to the core functionality
> of the app … The core functionality, as well as any core features that comprise this core functionality, must all be
> prominently documented and promoted in the app's description."
> "When an app's core functionality requires All files access permission, the developer must complete the Permissions
> Declaration Form and receive approval from Google Play."

**Permitted uses (complete list as published):**

> "For apps requesting access to the All files access permission, intended and permitted use includes file managers,
> backup and restore apps, anti-virus apps, and document management apps."
>
> - **File management**: "App's core purpose involves the access, editing, and management (including maintenance) of
>   files and folders outside of its app-specific storage space"
> - **Backup and restore apps**: "App must have a need to automatically access multiple directories outside of its
>   app-specific storage space for the purpose of backup and restore"
> - **Anti-virus apps**: "App's core purpose is to scan the device and provide anti-virus security features to the device user"
> - **Document management apps**: "Apps that must locate, access, and edit compatible file types outside of its
>   app-specific or shared storage". "App must justify in their Console declaration why the solutions documented in the
>   Access app-specific files or Storage Access Framework (a privacy-friendly option) documentation are not sufficient
>   for their purpose"
> - **Search (On Device)**: "App's core purpose is to search through files and folders across the device's external storage"
> - **Disk/Folder Encryption and Locking**: "App's core purpose is to encrypt files and folders"
> - **Device Migration/Phone transfer**: "App's core purpose is to help the user migrate to a new device"
> - "* Subject to Google Play review and approval."

**Exceptions:** "Google Play may provide a temporary exception … when: The use of the permission enables the app's core
functionality. There is currently no alternative method … or the use of privacy-friendly alternatives … has a
substantially detrimental impact on the critical features … The impact on user privacy is mitigated by security and
privacy best practices. The developer must justify in their Console declaration why the Storage Access Framework or
MediaStore API is not sufficient".

**Invalid uses:** "Media Files access" and "Any File selection activity where the user manually selects individual
files". "This list is not exhaustive."

**Alternatives named by Google:** "Access of Media Files: … MediaStore API"; "User selects files for
importing/transferring/processing: Developers should consider using Storage Access Framework".

**Re-declaring:** "If you change how your app uses these restricted permissions, you must submit the form again …
Deceptive and undeclared uses of these permissions may result in a suspension of your app and/or termination of your
developer account."

Developer docs ([Manage all files](https://developer.android.com/training/data-storage/manage-all-files), page date
2026-11-13) list "file manager apps, backup and restore apps, and document management apps" as examples. Technical
limits that support a justification ([Access documents and other files](https://developer.android.com/training/data-storage/shared/documents-files),
page date 2026-11-13): "On Android 11 (API level 30) and higher, you cannot use the `ACTION_OPEN_DOCUMENT_TREE` intent
action to request access to … The root directory of the internal storage volume … The Download directory." Also, under
scoped storage `MediaStore.Files` "shows only the photos, videos, and audio files that your app has created"
([Media](https://developer.android.com/training/data-storage/shared/media), page date 2026-11-12). So an "all PDFs and
Office files on this phone" list cannot be built from MediaStore alone.

**Applies to ScanPro:** yes. **Risk assessment (our judgement, NOT VERIFIED):**

- "Document management" is a listed permitted use. But its wording is "locate, access, and **edit** compatible file
  types", and it requires a justification of why SAF isn't enough. ScanPro's use is read-only listing and opening.
- Images fall under "Media Files access" (invalid; use MediaStore / Photo Picker). Opening a file the user picks falls
  under "File selection activity" (invalid; use SAF).
- The October 2026 clarification "reiterated minimum scope principles", which suggests stricter reviews. **Rejection
  risk for a scanner/PDF app is material.** To stand a chance, a device-wide document library (find every PDF and
  Office file without the user picking folders, including `Download/` and the storage root, which SAF tree access
  can't grant) must be a *core*, prominently described feature in the listing.

**ScanPro must:**

1. Keep every flow working without the permission: SAF `ACTION_OPEN_DOCUMENT` / `OPEN_DOCUMENT_TREE`, Photo Picker or
   MediaStore for images, and `ACTION_CREATE_DOCUMENT` or app storage for output.
2. If the permission is kept, fill in the Permissions Declaration Form (Document management, or File management if it
   really manages files). Justify with the `Download`/root SAF restriction and the cost of repeated pickers, attach a
   demo video if asked, and describe the "All documents on your phone" feature prominently in the full description.
3. Have a fallback ready: a Play flavour without `MANAGE_EXTERNAL_STORAGE` (the sideload build may keep it) so that a
   rejection doesn't block release.

## 7. Foreground service types (dataSync)

[Understanding foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
(page date: not shown):

> "When your apps target Android 14 and above, you'll need to declare any foreground service types that you use in a new
> declaration on the App content page … For each foreground service type you declare, you'll need to: Provide a
> description of the app functionality … Describe the user impact if: the task is deferred by the system … the task is
> interrupted by the system … Include a link to a video demonstrating each foreground service feature."

`TYPE_DATA_SYNC` use cases in the Console list:

> "Local processing: Import or export … Show the user manually triggering an import or export of files locally"
> "Local processing: Other. Use for specifically user-initiated work and not for regular system or server-initiated
> tasks. For example, encryption, resizing. Show a user initiating a local task like resizing or encrypting a file. The
> video must show direct user initiated action, not automatic background tasks."
> "If your use case is for transferring data over a network … in response to an explicit user request, we recommend
> using the user-initiated data transfer job instead of the dataSync foreground service type."

[Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) (page date
2026-11-16): dataSync covers "Import or export operations … Local file processing"; "Apps that target Android 15 or
higher are not allowed to launch a data sync foreground service from a `BOOT_COMPLETED` broadcast receiver."
`shortService`: "Can only run for a short period of time (about 3 minutes)." `mediaProcessing` is for "converting media
to different formats".

[FGS timeout](https://developer.android.com/develop/background-work/services/fgs/timeout) (page date 2026-11-16):

> "The system permits `dataSync` and `mediaProcessing` foreground services to run for a total of 6 hours in a 24-hour
> period, after which the system calls the running service's `Service.onTimeout(int, int)` method … If the service does
> not call `Service.stopSelf()`, the system throws an internal exception." "if the user brings the app to the
> foreground, the timer resets".

No official statement was found that dataSync is deprecated or scheduled for removal: **NOT VERIFIED**. The alternatives
page lists WorkManager, user-initiated data transfer jobs (network transfers), `shortService` ("File processing under 3
minutes") and `mediaProcessing`.

**Applies:** yes. **Must do:** declare `dataSync` → "Local processing: Other" (PDF merge, compress, OCR of a
user-chosen file), with user impact text and a demo video (unlisted YouTube or similar) showing the user tapping, say,
Merge and the notification. Start the service only from a user action in the foreground. Keep `onTimeout` →
`stopSelf()` (it is already implemented).

## 8. Data safety form and ML Kit

[Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469):

> "This includes data collected and handled through any third-party libraries or SDKs used in their apps."
> "You must reflect data collection or sharing carried out by such third-party code in the Data safety form for your app."
> "On-device access/processing: User data accessed by your app that is only processed locally on the user's device and
> not sent off device does not need to be disclosed."
> "developers can only declare encryption in transit if it applies to all user data that their app (including all its
> SDKs and libraries) collects and transmits off the user's device." (SDK guidance template)

[ML Kit: Prepare for Google Play's data disclosure requirements](https://developers.google.com/ml-kit/android-data-disclosure)
("Last modified: May 14, 2025"; page date 2026-07-15):

> "as the app developer, you are solely responsible for deciding how to respond to Google Play's Data safety section form"
> "For the collected data listed on this page, ML Kit encrypts the data in transit using HTTPS."
> "For the collected data listed on this page, ML Kit does not transfer this data to third-parties."

Data collected in **all** features, each "Used for diagnostics and usage analytics": device information
(manufacturer, model, OS version and build, ML accelerators); application information (package name, app versions);
device or other identifiers ("For thin features: Device identifier … Per-installation identifiers"; "For bundled
features: Per-installation identifiers"); performance metrics (latency); API configuration (image format, resolution);
feature input and output size; feature version; event type; error codes.

Feature-specific: barcode scanning collects extra data only "If you enable auto zoom option". Language ID collects
"Identified languages". Translate collects "Configured source and destination languages" and uses "Firebase remote
config and Firebase installations".

Gaps:

- Text recognition v2 and the Document Scanner have no feature-specific row on the page, so only the all-features data
  applies. The page doesn't define "thin" (we read it as the Play services `play-services-mlkit-*` artifacts ScanPro
  uses by default: NOT VERIFIED).
- **No documented way to turn off this ML Kit diagnostics logging was found: NOT VERIFIED that it can be disabled.**

**Applies:** yes. ScanPro's own code sends nothing, but ML Kit does. **Must do (our reading):** in the Data safety form
declare collection (not sharing) of "App info and performance → Diagnostics", "Device or other IDs" (and "App
activity / other actions" if you treat event types as that) for the purposes "Analytics" and "App functionality"
(diagnostics). Declare encrypted in transit; not shared. Images and OCR text stay on device and need no declaration.
State the same in the privacy policy, and add Firebase installation and remote config entries if the `:translate`
module is shipped.

## 9. Financial features declaration

[Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271):

> "All developers that have an app published on Google Play must complete the Financial features declaration, including
> apps on closed testing, open testing, or production tracks."
> "Even developers with apps that do not offer any financial features must complete this form and certify that no
> financial features are offered through the app."
> Categories include "Payments and transfers: Mobile payments and digital wallets; Money transfer and wire services". "If
> their app(s) don't provide any financial features, developers must declare: My app doesn't provide any financial features".

The page has no guidance on apps that only generate a payment QR code or hand off to another app. **NOT VERIFIED.**
Our reading: ScanPro holds no funds, starts no payment and stores no payment credentials (the UPI app does the payment),
so "My app doesn't provide any financial features" fits best. If a reviewer disagrees, "Mobile payments and digital
wallets" is the nearest category. The UPI QR feature should be described in the listing as generating a QR code for the
user's own UPI ID.

Billing questions are conditional. Play Billing applies only to digital goods sold in the app (section 15). ScanPro sells
nothing.

## 10. Health apps declaration

[Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291):

> "All developers that have an app published on Google Play must complete the Health apps declaration … Even developers
> with apps that do not offer any health features must complete this form and certify that no health features are
> offered through the app." ("After August 31, 2024, all apps will be required to have completed an accurate Health apps
> declaration.")

**Applies:** the form, yes. **Answer:** "My app doesn't provide any health features". ScanPro has no health category
feature, and scanning a medical document is general document handling. That is our reading: NOT VERIFIED.

## 11. Personal accounts: closed testing, trader status

[App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465):

> "Developers with personal accounts created after November 13, 2023, must run a closed test for their app with a
> minimum of 12 testers who have been opted in continuously for at least 14 days."
> "Testers who opt in, test for fewer than 14 days, and then opt out do not count toward the requirement."

No change to 12/14 was found as of today: VERIFIED. It doesn't apply to organisation accounts.

[Trader status declaration](https://support.google.com/googleplay/android-developer/answer/17594752):

> "all active developer accounts must declare whether they are a trader or non-trader under European Economic Area (EEA)
> and United Kingdom (UK) laws in Play Console. This declaration must be completed by February 1, 2027, regardless of
> where apps are targeted or distributed."
> "All new developer accounts are required to submit the declaration immediately upon account creation."
> "Failure to declare your trader status by the February 1, 2027 deadline may trigger enforcement including app update
> blocks and removal from the Play Store globally."
> "If you don't distribute apps in the European Economic Area or the United Kingdom, you can declare as a non-trader."

Traders' contact details (individuals: "a phone number and address") become visible from 1 March 2027. **Must do:** the
owner decides trader or non-trader. A free, ad-free hobby app is likely non-trader, but the page warns that misdeclaring
can lead to "account suspension or termination". This is the owner's legal judgement.

## 12. Store listing assets and metadata policy

[Store listing](https://support.google.com/googleplay/android-developer/answer/9859152): app name **30** characters,
short description **80**, full description **4,000**; "A contact email address is required."

[Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151): icon "32-bit PNG (with alpha)",
"512px by 512px", max "1024KB"; feature graphic "JPEG or 24-bit PNG (no alpha)", "1024px by 500px"; screenshots "JPEG or
24-bit PNG (no alpha)", shortest side 320 to 3840 px, long side no more than twice the short side, "up to 8 screenshots
for each supported device type", at least two to publish; video is a public or unlisted YouTube URL, ads off.

[Metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842): "Your app title must be 30
characters or less." "Don't use emojis, emoticons, or repeated special characters". No ranking or price claims ("#1",
"App of the year", "10% off"), no "unattributed or anonymous user testimonials", no keyword stuffing.

## 13. Play App Signing and AAB size

[About Android App Bundles](https://support.google.com/googleplay/android-developer/answer/9844279): "Starting August 2021,
new apps are required to publish with the Android App Bundle on Google Play." "To use app bundles, you must enrol in Play
App Signing."

[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756): the upload key "Must be an
RSA key of 2048 bits or more"; "If compromised or lost, Google can reset it". The app signing key "cannot be reset if you
manage it yourself and lose it". For distribution outside Play, either "download a signed, universal APK from Play
Console", or "Generate the app signing key you want to use for all app stores and transfer a copy of it to Google".
Annual key upgrade for installs on API 37+.

[App size limits](https://support.google.com/googleplay/android-developer/answer/9859372): base module **500MB**, each
feature module 500MB, install-time total 4GB; "If your app is above 200MB in size, users on a mobile data connection
will see a non-blocking dialog"; APK uploads limited to 100MB.

**Must do:** decide the key strategy before the first upload. If the sideload APK must update Play installs (and match
developer verification), either upload your own key to Play App Signing or distribute Play's universal APK.

## 14. Privacy policy

[User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311):

> "All apps must post a privacy policy link in the designated field within Play Console, and a privacy policy link or
> text within the app itself. The privacy policy must … comprehensively disclose how your app accesses, collects, uses,
> and shares user data, not limited by the data disclosed in the Data safety section. This must include: Developer
> information and a privacy point of contact or a mechanism to submit inquiries … types of personal and sensitive user
> data … Secure data handling procedures … The developer's data retention and deletion policy. Clear labeling as a
> privacy policy".
> "Apps that do not access any personal and sensitive user data must still submit a privacy policy."
> "Please make sure your privacy policy is available on an active, publicly accessible and non-geofenced URL (no PDFs)
> and is non-editable."

The account-deletion rules apply only to apps with accounts, so ScanPro is exempt. **Must do:** publish
`docs/privacy-policy/index.html` at a public HTTPS URL; put the link in Console and in the app's Settings/About. Name
the developer as in the listing, cover camera and file use (on device only), the ML Kit diagnostics from section 8, the
UPI hand-off, and a contact email.

## 15. Play Billing Library

[Play Billing Library deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq) (page date
2026-11-13):

> "By Aug 31, 2026, all new apps and updates to existing apps must use Billing Library version 8 or later. If you need
> more time to update your app, you can request an extension until Nov 1, 2026."

Table: v7 out from 31 Aug 2026 (extension 1 Nov 2026); v8 from 31 Aug 2027 (1 Nov 2027); v9 from 31 Aug 2028.

**Applies:** no. ScanPro has no billing dependency and sells no digital goods. This only matters if in-app purchases are
added later (then use PBL 8+, preferably 9).

---

### Pages that disagree or couldn't be confirmed (all NOT VERIFIED)

- 16 KB phone-update enforcement date: 1 Nov 2025 (2025 blog) vs 1 Feb 2027 (developer.android.com, 2026-11-04) vs
  "current requirement" (Play quality page).
- Whether Play explicitly accepts `targetSdk 37`: no official sentence found; the "36 or higher" rule implies it.
- Whether ML Kit diagnostics can be switched off: nothing documented.
- What ScanPro should answer in the Financial features and Health declarations: our reading, not Google's words.
- Whether Google will approve `MANAGE_EXTERNAL_STORAGE` for ScanPro: only Google's review decides.
- Exact 2027 date for global developer verification enforcement: not published.
