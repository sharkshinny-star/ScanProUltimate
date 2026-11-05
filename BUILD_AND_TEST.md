# Build and test

Last updated: 2026-11-05, security hardening commit on top of `10fa630` (tests, lint, Play bundle). Supersedes `BUILD_STATUS.md` (8 October 2026, written before the project
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

CI: `.github/workflows/android.yml` runs unit tests, lint, `bundleRelease`, the sideload build and the size report.

## Latest results (2026-11-05, F48 review fix and uiautomator 2.4.1, run on `263325d` + that commit, before rebasing onto `959ce27`; the rebased tree compiles)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 320 tests: 290 passed, 30 skipped (opt-in screenshot and real-photo tests), 0 failed. Includes `PdfPictureTest` (6), `MergePasswordTest` (6), `WelcomePermissionsTest` (5) from F48–F50 |
| `:app:lintDebug` (analysis rerun) | 0 issues (the `GradleDependency` warning is gone with uiautomator 2.4.1) |
| `:baselineprofile:compileNonMinifiedReleaseKotlin` | Passed with uiautomator 2.4.1 (earlier in the session; profile not generated, no device) |
| `:app:bundleRelease` | Passed; AAB 45.9 MB |
| Sideload `assembleRelease` | Passed; arm64 33.6 MB, armeabi-v7a 25.6 MB, x86_64 39.4 MB, universal 82.7 MB (unsigned) |
| Not run | Device or emulator, instrumented tests, screenshot renders, `:opencv:lintDebug`, 16 KB alignment |

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
