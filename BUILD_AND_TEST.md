# Build and test

Last updated: 2026-11-09, `a352579` (unit tests rerun after F96; the other rows below keep the date and commit they were run on). Supersedes `BUILD_STATUS.md` (8 October 2026, written before the project
had ever been built).

## Toolchain

| Tool | Version |
|---|---|
| JDK | 17 or newer |
| Gradle (wrapper) | 9.8.2 |
| Android Gradle Plugin | 9.5.0 |
| Kotlin / Compose compiler | 2.4.21 |
| KSP | 2.3.13 |
| Android SDK | platform 37 (`compileSdk 37`), `targetSdk 37`, `minSdk 24` |

`local.properties` (gitignored) must contain `sdk.dir=<path to Android SDK>`.

## Commands

```bash
./gradlew :app:compileDebugKotlin                 # fast compile check
./gradlew :app:testDebugUnitTest                  # JVM unit tests
./gradlew :app:lintDebug                          # Android lint (config: app/lint.xml)
./gradlew :app:bundleRelease                      # Play bundle, R8 on, with :translate as on-demand module
./gradlew -Pscanpro.sideload=true :app:assembleRelease   # per-ABI + universal APKs, translation built in
scripts/ci/size-report.sh app/build/outputs/apk/release app/build/outputs/bundle/release
```

Gradle properties:

| Property | Effect |
|---|---|
| `-Pscanpro.sideload=true` | Per-CPU APKs, no dynamic feature (Android disallows both together) |
| `-Pscanpro.bundledMl=true` | ML Kit models inside the APK (~16 MB larger per ABI) instead of Play-services downloads |
| `-Pscanpro.photoDir=<dir>` | Enables `RealPhotoEquivalenceTest` (skipped otherwise) |
| `-Pscanpro.updateGoldens=true` | Rewrites golden images for `MatFiltersGoldenTest` |
| `-Pscanpro.screenshots=true` | Enables `ScreenScreenshotTest` (run it with `--tests '*ScreenScreenshotTest*'`, about 3 min): 12 real screens × light/dark to `app/build/screenshots/*.png`, 1080 × 2400 px (360 × 800 dp at 3x) |
| `-Pscanpro.composeMetrics=true` | Compose compiler metrics in `build/compose_metrics` |
| `-Pscanpro.privacyPolicyUrl=…`, `-Pscanpro.termsUrl=…` | Settings links (hidden when empty) |

CI: `.github/workflows/android.yml` runs unit tests, lint, `bundleRelease`, the sideload build and the size report, on pushes to `main`, pull requests and manual runs (20-minute timeout; artifacts only on manual runs). Not yet run with the new limits; if the build exceeds 20 minutes, raise `timeout-minutes`.

## Latest results (2026-11-09, working tree on branch claude/awesome-clarke-ussjo3)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` (F97 Convert tools overhaul) | 353 passed, 63 skipped, 0 failed; `PdfToolsFeaturesTest` 11 passed (empty image list rejection, password-protected PDF rejection, Office/HTML/Markdown MIME types detection); `:app:assembleDebug` passed. |
| `:app:testDebugUnitTest` (F96, rebased tree at `2149caf`, 2026-11-09) | 413 tests, 30 skipped, 0 failed (412 before the rebase onto F91-F95); `:app:compileDebugKotlin` clean; not seen on a device |
| Camera <-> Files 10-switch performance & freeze fix (F91) | Physical OnePlus CPH2569 (Android 15): 10 consecutive automated switches between Files and Camera via ADB. Freezes of 1,700 ms and 2,100 ms completely eliminated (0 frames > 250 ms across 881 rendered frames). 50th percentile: 12 ms; 90th percentile: 28 ms; 95th percentile: 40 ms; 99th percentile: 200 ms. High input latency frames dropped from 4,998 to 534. Janky frames reduced to 18.39% (from 25.86% + legacy 70.36%). 0 Camera HAL stream configuration failures during active binds. |
| `:app:testDebugUnitTest` (F90–F95, run by the other agent, no counts or commit recorded) | Reported successful; superseded by the F96 row above, which reran the whole suite on the combined tree. |
| `:app:assembleDebug` (2026-11-09, other agent, commit not recorded) | Full build with all 74 tasks executed: BUILD SUCCESSFUL in 8m 14s; debug APK generated cleanly and verified installable. |
| `:app:connectedDebugAndroidTest` (earlier run, before F96; not rerun) | Ran on connected physical device OnePlus CPH2569 (Android 15, API 35): 6 passed, 0 failed, 0 ignored (`AppSmokeTest.smokeFlow`, `LanguageChangeDeviceTest.appLanguageChangeRestartsCleanly`, `LaunchDeviceTest.appLaunchesToRecentList`, `LaunchDeviceTest.backFromNewScanReturnsHome`, `IdCardDeviceTest.detectsIdCornersAndPerspectiveCorrects`, `IdCardDeviceTest.composedIdCardFitsA4`). |
| `:app:lintDebug` (earlier run, before F92-F96; not rerun) | 0 errors, 1 warning (the Gradle 9.9.0 notice), 11 hints. |
| Device checklist & smoke tests | Physical OnePlus CPH2569: (1) R42 search pill clipping checked across 10 tab switches (`r42_home_01.png` to `r42_home_10.png`), rounded top whole with zero clipping; (2) F81 refactor smoke test verified end-to-end: scan page, edit with filter, undo, delete page, merge two documents into "Strong + Strong copy", share PDF through system sharesheet, empty Recycle bin (`f81_recycle_bin_emptied.png`); (3) R38 rotation pass verified on device: camera adjustments (`r38_camera_adjustments_landscape.png`), Pen dialog (`r38_pen_dialog_landscape.png`), merge dialog (`r38_merge_dialog_landscape.png`), PDF viewer search (`r38_pdf_viewer_search_landscape.png`), state cleanly survived; (4) Document tab labels in Hindi, Tamil and Telugu verified on device under per-app locales, fitting within 360 px width without truncation; (5) R40/F82 password confirm-twice and R41/F84 QR sharing safety verified. |
| Screenshot review (`docs/device-screens`, 33 files, read 2026-11-08) | **Supports:** R42 (the search pill is whole in all 10 Home shots; shot 3 has a OnePlus App Lock banner over it, the pill is unchanged), F81 (empty Recycle bin, which says documents wait 7 days, the real default), the R38 landscape dialogs (Pen, Merge, PDF viewer search, camera adjustments, Adjust page), the Hindi camera, capture, Files and document screens, and Hindi and Telugu document tabs fitting. **Does not show what its name says:** `r40_backup_all_dialog.png` is Android's folder picker ("Can't use this folder"), so the confirm-twice password dialog (R40) is not evidenced; `r39_lockscreen_landscape_font2.png` is Settings and `r39_welcome_landscape_font2.png` is Home, so the Lock and Welcome layouts at large font (R39) are not evidenced; `r39_app_lock_toggled.png` was taken mid-rotation. **Shows problems:** R43 to R46 in `AUDIT_FINDINGS.md`. |
| Not run | After F96: lint, `connectedDebugAndroidTest`, `bundleRelease` and the sideload build; on a device, print with an in-app language on Android 12 or older and share PNG pages (F96). Always: release signing; tablet and foldable hardware tests (R2). |

Earlier results (2026-11-08, `18c89a5`):

### Earlier (2026-11-08, `18c89a5` + CI memory limits, run in the cloud container with the Android SDK installed by hand)

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` | Passed, 0 warnings |
| `:app:testDebugUnitTest` (with `--max-workers=2 -Pscanpro.testHeap=2560m`, the new CI limits) | 336 tests: 306 passed, 30 skipped (opt-in), 0 failed. Closes part (1) of R36 (the full run owed since F65) |
| `:app:lintDebug` | 0 errors, 2 warnings (not inspected); the `UnrememberedMutableState` errors that failed CI runs 49 and 53 are gone |
| Not run | `bundleRelease` and sideload `assembleRelease` (left to CI), device checks. CI run 72 on `18c89a5` stopped by a runner shutdown at about 12 minutes (same as runs 68, 70, 71); the memory limits are the attempted fix and are unverified on a CI runner until the next push |

Earlier results (2026-11-05):

**2026-11-08, combined tree (merge `2dc8429` = F66–F68 committed, plus uncommitted F69/F70):** `:app:testDebugUnitTest` 356 tests, 297 passed, 59 skipped (opt-in), 0 failed; `:app:assembleDebug` passes. This is the first run with the fast save (F68) and the flash/review changes (F69) together. Not run: lint, release builds; the combined build is **not yet on the phone** (disconnected). Beware: the merge plus a stale editor save had reverted `AUDIT_FINDINGS.md`/`AGENT_HANDOFF.md`; both were reconstructed by hand afterwards (F68 row restored, mine renumbered F69/F70).

**2026-11-08, local Mac, working tree on `259deeb` (uncommitted changes included):** `:app:testDebugUnitTest` 350 tests, 291 passed, 59 skipped (opt-in), 0 failed. **First device run:** `AppSmokeTest` passed (1/1) on a OnePlus CPH2569, Android 15 (API 35), arm64; installed with `adb install -r` and run with `am instrument`, not `connectedDebugAndroidTest`, which uninstalls the app and its data. The shell needs `JAVA_HOME` set to Android Studio's JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`); without it `./gradlew` fails at once, and old XML results look like a pass. ColorOS asks on the phone to allow each USB install. Not run: lint, release builds, manual device checks.

**2026-11-08, after the device layout pass (F70; this run predates the fast-save commit `c27ad4b`, whose F68 is unrelated), working tree on `6ddbdf5`:** `:app:assembleDebug` passed, no warnings in the touched files; `:app:testDebugUnitTest` 352 tests, 293 passed, 59 skipped, 0 failed. The layout pass itself (Home, Files, Tools, Settings, camera, launch screen; light and dark; 150 % font; landscape) ran on the previous build. The fixed build is **not yet installed or seen on the phone** (its install did not complete). Not run: lint, release builds.

**2026-11-08, scanning fixes (F69):** `ScanReview*` tests 13/13 on the final tree; `:app:assembleDebug` passes. On the phone (one build before the final polish): torch lights the moment it is picked, the flash button cycles Off/Auto/On/Torch, the review opens on the finished page, Crop restores the handles. The final build (Crop panel without sliders) is built but **not yet installed** (the phone disconnected). Full suite and lint not re-run after F69.

**2026-11-08, launch timing on the OnePlus (debug build):** first frame (`am start -W` TotalTime) 2.7 s uncompiled, 1.4-1.9 s after `cmd package compile -m speed-profile` (close to a Play install); the `ScanProLaunch` log shows the settings were ready each time (0.9-1.4 s), so the 1.5 s cap is not what ends the wait. A release build with R8 and the baseline profile should be faster; not measured (outputs unsigned). `LaunchIntroTest` 3/3 and `LaunchSmokeTest` 70/70 pass after the launch change.

## Latest results (2026-11-05, F48 review fix and uiautomator 2.4.1, run on `263325d` + that commit, before rebasing onto `959ce27`; the rebased tree compiles)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 320 tests: 290 passed, 30 skipped (opt-in screenshot and real-photo tests), 0 failed. Includes `PdfPictureTest` (6), `MergePasswordTest` (6), `WelcomePermissionsTest` (5) from F48–F50 |
| `:app:lintDebug` (analysis rerun) | 0 issues (the `GradleDependency` warning is gone with uiautomator 2.4.1) |
## Latest results (2026-11-09, Images to PDF feature audit and overhaul, F92)

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` | Passed, 0 errors |
| `:app:testDebugUnitTest --tests "com.example.scanpro.feature.tools.PdfToolsFeaturesTest"` | Passed; includes `rotateImageFileProducesRotatedImageForPdf` (aspect ratio transformation verified) |
| `:app:testDebugUnitTest` | Full suite passed; 0 failures (67 tasks up-to-date/executed) |
| Release builds, device | Not run |

## Earlier results (2026-11-09, camera/files switching freeze fix, F91)

## Earlier results (2026-11-05, after-scan sheet F65, on `f93c43c`)

| Check | Result |
|---|---|
| `:app:compileDebugUnitTestKotlin` | Passed, 0 compiler warnings |
| `ScanSavedSheetTest` | 5 passed; `app/build/screenshots/scan_saved_receipt.png` and `scan_saved_card.png` looked at |
| Full `:app:testDebugUnitTest`, `:app:lintDebug` | **Not completed**: the run was stopped by the owner (see R36). The last full run (331 tests, 0 failed; lint 0 errors) is the F64 entry below |
| Release builds, device | Not run |

## Earlier results (2026-11-05, page quality check and glare hint, on `0d74480`)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 331 tests, 301 passed, 30 skipped, 0 failed; 0 compiler warnings |
| `PageQualityTest`, `ScanReviewQualityTest` | 7 + 3 passed; `app/build/screenshots/review_quality_notice.png` looked at (notice above the page, corner handles free) |
| `:app:lintDebug` | 0 errors, 1 warning (`GradleDependency`, a newer library version; not from this change) |
| Release builds, device | Not run |

## Earlier results (2026-11-05, launch animation from the Figma design, on `7eee16f`)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 320 tests, 290 passed, 30 skipped, 0 failed (includes `LaunchSmokeTest`: 56 launches on Android 7-17, and `LaunchIntroTest`: 3); 0 compiler warnings |
| `LaunchIntroTest` | Reveal at 1.18 s and end at 1.6 s, each once; a tap skips to the exit; 12 frames rendered to `app/build/screenshots/intro_*.png` and compared side by side with the Figma render (match; frame 0 deliberately has no icon shadow, matching the system launch screen) |
| `:app:lintDebug` | 0 errors, 1 warning (`GradleDependency`, a newer library version; not from this change) |
| Release builds, device | Not run |

## Earlier results (2026-11-05, security hardening commit on top of `10fa630`)

| Check | Result |
|---|---|
| `:app:compileDebugKotlin --rerun` | Passed, 0 Kotlin warnings in app code (one Gradle-script deprecation in `app/build.gradle.kts:100`, `srcDir`, predates this commit) |
| `:app:testDebugUnitTest` | 313 tests: 283 passed, 30 skipped (28 `ScreenScreenshotTest` cases, opt-in; 2 `RealPhotoEquivalenceTest`), 0 failed. New in this commit: 5 in `EncryptedDocumentPackageTest`, 2 in `KeptScanStoreTest` |
| `:app:lintDebug` | 0 errors, 1 warning (`GradleDependency`: uiautomator 2.4.1 available) |
| `:app:bundleRelease` | Passed (R8); AAB 45.8 MB; merged release manifest has `android:taskAffinity=""` |
| Gradle wrapper checksum | A fresh download (empty `GRADLE_USER_HOME`) was verified against the pinned `distributionSha256Sum`; `gradle-wrapper.jar` matches a published Gradle wrapper checksum |
| Sideload `assembleRelease`, 16 KB alignment, `:opencv:lintDebug`, screenshots | Not run this time (last passed on `b76720c`, below) |
| Instrumented tests, device checks (password keyboards, `taskAffinity`, backup speed with 600k rounds) | Not run: no emulator or device |
| Release signing | Not validated: no secrets; outputs unsigned |

Older runs, kept for history:

### 2026-11-05, colour themes / Home tips / What's new, rebased on `d2c1451`

| Check | Result |
|---|---|
| `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin` | Passed, 0 compiler warnings |
| `:app:testDebugUnitTest` | 306 tests, 276 passed, 30 skipped, 0 failed (includes the new `ColourThemeScreenshotTest`, 2 tests, which always renders Home and Settings in several colour themes to `app/build/screenshots`; renders looked at) |
| `:app:lintDebug` | 0 errors, 1 warning: `GradleDependency`, uiautomator 2.4.1 is available (not from this change). Fixed on the way: 47 `MissingTranslation` for the new strings, 5 `UnrememberedMutableState` in `ScanReviewNamingTest`/`ScanReviewSlidersTest` |
| Release builds, `:translate`, `:opencv`, device | Not run |

## Earlier results (2026-11-04, `b76720c`, full run in a cloud container)

**Every check below was run on `b76720c` (screen-review fixes, F43) unless a row says otherwise.**

| Check | Result |
|---|---|
| `:app:compileDebugKotlin --rerun` | Passed, **0 compiler warnings** (the four older deprecation/inline warnings were fixed in `ab4e72d`) |
| `:app:testDebugUnitTest` | 275 tests: 245 passed, 30 skipped (28 `ScreenScreenshotTest` cases, opt-in; 2 `RealPhotoEquivalenceTest`), 0 failed. New: `DocumentSearchSnippetTest` (3, real Room/FTS4 under Robolectric), one `OfficeToHtmlTest` case |
| `ScreenScreenshotTest` (`-Pscanpro.screenshots=true`) | 28 renders (14 screens × light/dark, including the new landscape review and text-search hit), all looked at |
| `:app:lintDebug`, `:opencv:lintDebug` | 0 issues each |
| `:translate:testDebugUnitTest`, `:opencv:testDebugUnitTest` | No tests in those modules (NO-SOURCE) |
| `:app:bundleRelease` | Passed; AAB 45.7 MB |
| Sideload `assembleRelease` | Passed; arm64 33.5 MB, armeabi-v7a 25.5 MB, x86_64 39.3 MB, universal 82.6 MB (unsigned) |
| 16 KB page alignment | arm64 and x86_64: all 8 `.so` files each have 16 KB `LOAD` alignment (`readelf`) |
| Instrumented (`AppSmokeTest`), baseline profile, performance | Not run: no emulator or device |
| Release signing | Not validated: no secrets; outputs unsigned |

**2026-11-02, screenshot tests and F41/F42 on top of `bd3cacd`:** `:app:testDebugUnitTest` 267 tests, 241 passed, 26 skipped (24 `ScreenScreenshotTest` cases, opt-in; 2 `RealPhotoEquivalenceTest`), 0 failed; `:app:lintDebug` 0 issues; 0 compiler warnings. `ScreenScreenshotTest` with `-Pscanpro.screenshots=true`: 24 PNGs, checked by eye. Release builds, `:translate`, `:opencv` not re-run.

**Re-run on `07293b0` (F38, F39 and F40 included) in a fresh cloud container, 2026-11-01:** compile, the unit tests and lint below. **Not re-run on it:** `:translate:compileDebugKotlin`, `:opencv:lintDebug`, both release builds and the 16 KB alignment check; those rows are from the commit before F38. None of the F38 to F40 screens has been seen on a device.

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` | Passed on `07293b0`, 0 warnings. `:translate:compileDebugKotlin` not re-run |
| `:app:testDebugUnitTest` | On `07293b0`: 243 tests: 241 passed, 2 skipped (`RealPhotoEquivalenceTest`, needs `-Pscanpro.photoDir`), 0 failed |
| `:app:lintDebug` | On `07293b0`: 0 issues (`UseTomlInstead` ignored, see R37) |
| `:opencv:lintDebug` | 0 issues (every check ignored by `opencv/lint.xml`) |
| `:app:bundleRelease` | Passed; AAB 45.6 MB |
| Sideload `assembleRelease` | Passed; arm64 33.4 MB unsigned (budget 40) |
| 16 KB page alignment | arm64 and x86_64 `.so` aligned to `0x4000` (`readelf`) |
| `-Pscanpro.bundledMl=true` build | Not run |
| `LaunchSmokeTest` (Robolectric 4.17) | Passed, now 4 tests including the F37 Recents check (fails without the fix): on **API 24–37** (Android 7.0–17), 56/56: the app starts to its first screen, survives recreation, and applies a saved language while the Application is attached (F33 regression). JVM framework, not a device: camera, GPU, WebView printing and Play services are not exercised. Needs `-Xmx4g` and the two `--add-*` JVM flags in `app/build.gradle.kts` |
| Instrumented tests (`AppSmokeTest`), performance | Not run: no emulator or device in the container |
| Android 17 (targetSdk 37) on a device | Not run (see R2) |
| IDE inspection with the shared profile | Not run: no Android Studio in the container |
| Release signing | Not validated: no secrets available; outputs are unsigned |
| Manual UI pass | Not on a device; screenshot review done 2026-11-02 (F41, F42, R18) |

## Unit test inventory

`app/src/test`: imaging (`MatFiltersGoldenTest`, `MatFiltersEquivalenceTest`, `RealPhotoEquivalenceTest`, `CurvedPageTest`,
`PaperColorsTest`, `CropQuadTest`, `PageAdjustmentsTest`), geometry (`QuadMathTest`, `QuadGeometryTest`, `RectMathTest`),
documents/PDF (`PageRangesTest`, `PdfTextLayerMathTest`, `WordPlacementTest`, `FormFieldRulesTest`, `AnnotationRemapTest`,
`DocxExporterTest`, `XlsxExporterTest`, `EditHistoryTest`, `FileSafeNameTest`, `SmartTitleTest`), intelligence
(`DocumentIntelligenceEngineTest`, `BusinessCardParserTest`, `OcrLayoutTest`), library (`LibraryFiltersTest`,
`TrashCountdownTest`), backup (`EncryptedDocumentPackageTest`, 12 tests), crash (`CrashReporterTest`), work (`CacheCleanerTest`, 4 tests; `KeptScanStoreTest`, 4 tests), edit queue (`EditJournalTest`, 5; `DocumentEditTest`, 3).

`app/src/androidTest`: `AppSmokeTest` (needs a device).

## Known errors and workarounds

| CI runs 80-82 (2026-11-08) failed in under a minute: `Downloading https://services.gradle.org/distributions/gradle-9.8.2-bin.zip failed: timeout (10000ms)`, one attempt | The wrapper's 10 s timeout with `retries=0`, and CI's cache was read-only off `main`, so the distribution was fetched every run. Fixed: `networkTimeout=60000`, `retries=3`, `retryBackOffMs=2000` in `gradle-wrapper.properties`; the cache is written from the working branch too. Not a code failure; re-run if it recurs. |
| Problem | Fix |
|---|---|
| Maven Central HTTP 429 in cloud containers | Add `~/.gradle/init.d/central-mirror.gradle` that replaces `mavenCentral()` with `https://maven-central.storage-download.googleapis.com/maven2/`. Local only; never commit it. |
| `sdkmanager` 9862592 can't find `platforms;android-37` | Unzip `platform-37.0_r02.zip` into `platforms/android-37` and `build-tools_r37_linux.zip` into `build-tools/37.0.0` (from `dl.google.com/android/repository/`); Build-Tools 36 likewise if AGP's own download is slow. |
| Upgrading Gradle | The wrapper pins `distributionSha256Sum`. Run `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sum>` with the sum from `https://services.gradle.org/distributions/gradle-<v>-bin.zip.sha256`, or the wrapper refuses the download. |
| A dependency "could not be found" although Google's Maven repository has it | `google()` only serves `com.android.*`, `com.google.*` and `androidx.*`; add the new group to both filters in `settings.gradle.kts`. |
| Dependency downloads crawl (a few MB per minute) in a cloud container | Keep the `google()` content filters in `settings.gradle.kts`: without them every Maven Central artifact first waits about 2 s on a miss at Google's repository. |
| `ScreenScreenshotTest`: Files grid empty | Keep the v1 `createComposeRule` (deprecated, suppressed): under the v2 rule the grid never got Paging's first page. |
| Test JVM aborts (exit 134, "Class not found: java/nio/LongBuffer") | Robolectric native graphics, when only some `ScreenScreenshotTest` cases run (e.g. `--tests '*files*'`); run the whole class. |
| Compose UI test never goes idle (`AppNotIdleException`) once a dialog with a text field is open | Robolectric limitation here: even a plain Material `AlertDialog` + `OutlinedTextField` hangs, with or without focus and with a manual clock. Test the state around the dialog instead (as `ScanReviewNamingTest` does) and check typing on a device. |
| `CONNECT tunnel failed, response 403` for `dl.google.com` (2026-11-05) | The cloud environment's network policy blocks it. Google's Maven (`maven.google.com`) redirects every artifact there, and the SDK zips live there, so nothing builds. Add `dl.google.com` under Allowed domains in the environment's Network access settings. |
| `SDK location not found` | Install platform 37 and write `local.properties`. |
| `Could not resolve` / HTTP 429 from repo.maven.apache.org on a first build | Same Maven Central rate limit as above: create the local mirror init script, then rebuild (a first build with it took about 4 minutes). |
| `Package platforms;android-37 not found` in `sdkmanager` | Newer SDK managers call it `platforms;android-37.0`. |
| `primaryClip = clip` fails to compile (`val cannot be reassigned`) | `ClipboardManager` getter is nullable and setter non-null, so Kotlin exposes no settable property; keep `setPrimaryClip(clip)` despite the IDE hint. |
| IDE reports thousands of OpenCV warnings | Open the project so the shared profile in `.idea/inspectionProfiles/` loads, or run Inspect Code on the "App code" scope. |
| JVM tests see `#<id> args` instead of text | `AppStrings` has no Context in JVM tests; assert against `AppStrings.get(R.string.x, …)` or the exception's `messageRes`/args. |
| Lint `TrustAllX509TrustManager` in `bcpkix-jdk15to18` | Suppressed in `app/lint.xml`: BouncyCastle's TLS tooling is never reached (PDFBox uses it only for certificate-encrypted PDFs). |
| Material3 `@OptIn` "unnecessary" hints in the IDE | Keep them in `DocumentScreen`, `LibraryScreen` and two `PdfToolsScreen` functions: the compiler needs them. |
| OpenCV Java wrapper warnings | Ignore: generated from upstream by `scripts/opencv`; edits would be overwritten. |
| Building while files are mid-edit | Release builds compile whatever is on disk; rerun after edits finish. |

## How to record a new run

Update the "Latest results" table (date, commit, each check), move anything newly verified out of the "Not run" rows, and note
new failures in "Known errors". Mirror important outcomes in `AGENT_HANDOFF.md`.
