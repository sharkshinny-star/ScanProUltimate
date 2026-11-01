# Build and test

Last updated: 2026-10-31, after the "launch test on every Android version" commit. Supersedes `BUILD_STATUS.md` (8 October 2026, written before the project
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
| `-Pscanpro.composeMetrics=true` | Compose compiler metrics in `build/compose_metrics` |
| `-Pscanpro.privacyPolicyUrl=…`, `-Pscanpro.termsUrl=…` | Settings links (hidden when empty) |

CI: `.github/workflows/android.yml` runs unit tests, lint, `bundleRelease`, the sideload build and the size report.

## Latest results (2026-11-01, "fix start-up bugs" commit)

**Not re-run after F38** (editor discard dialogs, scan review and camera changes, post-save opening): the session had no Android SDK. The table below is for the commit before F38.

| Check | Result |
|---|---|
| `:app:compileDebugKotlin`, `:translate:compileDebugKotlin` | Passed, 0 warnings |
| `:app:testDebugUnitTest` | 243 tests: 241 passed, 2 skipped (`RealPhotoEquivalenceTest`, needs `-Pscanpro.photoDir`), 0 failed |
| `:app:lintDebug` | 0 issues (`UseTomlInstead` ignored, see R10) |
| `:opencv:lintDebug` | 0 issues (every check ignored by `opencv/lint.xml`) |
| `:app:bundleRelease` | Passed; AAB 45.6 MB |
| Sideload `assembleRelease` | Passed; arm64 33.4 MB unsigned (budget 40) |
| 16 KB page alignment | arm64 and x86_64 `.so` aligned to `0x4000` (`readelf`) |
| `-Pscanpro.bundledMl=true` build | Not run |
| `LaunchSmokeTest` (Robolectric 4.17) | Passed, now 4 tests including the F37 Recents check (fails without the fix): on **API 24–37** (Android 7.0–17), 56/56: the app starts to its first screen, survives recreation, and applies a saved language while the Application is attached (F33 regression). JVM framework, not a device: camera, GPU, WebView printing and Play services are not exercised. Needs `-Xmx4g` and the two `--add-*` JVM flags in `app/build.gradle.kts` |
| Instrumented tests (`AppSmokeTest`), screenshot, performance | Not run: no emulator or device in the container |
| Android 17 (targetSdk 37) on a device | Not run (see R2) |
| IDE inspection with the shared profile | Not run: no Android Studio in the container |
| Release signing | Not validated: no secrets available; outputs are unsigned |
| Manual UI pass | Not done |

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
| `SDK location not found` | Install platform 37 and write `local.properties`. |
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
