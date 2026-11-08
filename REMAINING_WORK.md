# Remaining work

Everything still open on `claude/awesome-clarke-ussjo3`, as of 2026-11-08 (after R42 and R9 step 1, commit `08188db`; CI run 87 passed on F80, the run after this covers R9 step 1 and R42). Generated from the "Open" table in
`AUDIT_FINDINGS.md`, which holds the full problem description and evidence for each ID; keep that table as the source
of truth and update this list when items close.

## Before release: device checklist

Nothing below has run on a phone; every check so far is a JVM test or render. The full
`./gradlew :app:testDebugUnitTest :app:lintDebug` was run after F80 (378 tests, 0 failed, lint 0 errors); CI run 87
covers F74-F80 together.

1. Run the three instrumented tests in Android Studio (`app/src/androidTest`: `LaunchDeviceTest`, `LanguageChangeDeviceTest`,
   `scanner/IdCardDeviceTest`, plus the older `AppSmokeTest`). They compile but have never run.
2. Launch animation (R24): cold start from the launcher on Android 12+ and on Android 11; with app lock on (no animation);
   rotate during it; share a file into the app (no animation). Also the F66 fixes: no dark start, no black frame after a
   language change.
3. Page-quality check (R25): blurred, glary and dark pages flagged in review with Retake; glossy covers, white-on-white
   pages and IDs with holograms not over-flagged; glare hint in the camera never blocks auto-capture.
4. "Scan saved" sheet (R36): a receipt (total + Copy total), a business card (Add to contacts), a letter (no smart action);
   the Searchable PDF badge and Copy text appear once text recognition finishes.
5. Camera work since run 86, all device-only: fast save and review-when-needed (F68), ID card both sides on one page
   (F67), camera strip with long-press delete and Undo (F74), duplicate-aware auto-capture (F75), Book mode two-page
   spreads and the spine guide (F80), the Scan tile and home-screen widget (F78).
6. R42 fix check on the OnePlus: switch tabs and back several times; the search pill's top must stay whole.
7. Colour themes, Home tips and What's new (R19) in light and dark; tablet side rail Scan button (F52).
8. Android 17 / targetSdk 37 on a phone and a tablet or foldable (R2).
9. Native-speaker check of the Hindi, Telugu, Kannada, Tamil and Malayalam strings added since F64 (R19, R36; the F73-F80
   strings are drafts). Marathi, Gujarati, Bengali and Punjabi are deliberately not added (owner decision).

## Owner decisions (resolved 2026-11-08)

- **Screenshots in debug builds**: Kept as-is. `MainActivity` skips `FLAG_SECURE` when `BuildConfig.DEBUG` to facilitate screenshot testing and screen recording during development/QA while locked. Release builds strictly enforce `FLAG_SECURE`.
- **R18 (Recycle bin actions on small phones)**: Closed as F85. "Empty" moved from the button row under the notice into the TopAppBar ⋮ dropdown menu. "Restore all" now spans the full width under the notice, preventing button crowding and text truncation on 360 dp screens.
- **R20 (Documents and database encryption at rest)**: Deliberately not changed / skipped. Documents, OCR text and database rely on Android file-based encryption and sandbox security. Encrypting with Keystore/SQLCipher/Tink AEAD adds key-loss risk and runtime performance penalty; app lock provides sufficient protection.
- **R21 (Exported activity internal actions)**: Deliberately not changed / skipped as low risk. Internal actions accepted via external intents only trigger internal navigation and require device/app unlock.
- **R22 (Crash reports file names)**: Deliberately not changed / skipped as low risk. Crash reports are strictly local on-device and never leave the device unless explicitly shared by the user.
- **R23 (ML Kit telemetry & internet permission)**: Kept. `INTERNET` permission is required for downloading on-device translation models; ML Kit telemetry disclosure must be declared on Google Play Data Safety form before release.
- **Translations mechanical check**: Verified complete. All 5 non-English languages (Hindi, Telugu, Kannada, Tamil, Malayalam) contain 100% of the 1,354 strings with exact matching placeholders to English; native speaker review will assess tone and nuance prior to store launch.
- R9 (splitting `DocumentRepository`) is done (F81); R39, R40 and R41 are done (F82-F84); R18 is done (F85).

## PDF Tools backlog

Seven tool improvements (password prompts in every tool, Unlock's "Done" notice, file names in Split and PDF to
Images, page numbers/watermark, Sign PDF, PDF to Word, insert pages) and the device checks for F48–F50:
see `TOOLS_IMPROVEMENTS.md`.

## Open items by priority

| ID | Priority | Area | Where | Open issue | Next step |
|---|---|---|---|---|---|
| R2 | P1 | Compat | `app/build.gradle.kts` `targetSdk = 37` | Raised to 37 (Android 17) after a code review against the Android 17 behaviour changes (see F12). | Device-test on Android 17, including a large screen. |
| R7 | P1 | Testing | whole app | Nothing verified on a device. | Device pass; Compose UI tests in CI on an emulator; golden comparison for the screenshots. |
| R19 | P2 | UX | `core/designsystem/Palettes.kt`, `core/designsystem/PalettePicker.kt`, `feature/library/HomeTips.kt`, `ui/WhatsNewSheet.kt`, `res/values-*/strings_themes_tips.xml` | Colour themes, Home tips and the What's new sheet are verified only by JVM renders (`ColourThemeScreenshotTest`) and tests. | Device pass through each colour theme in light and dark; native-speaker review of `strings_themes_tips.xml`. |
| R23 | P2 | Privacy / compliance | ML Kit dependencies, `AndroidManifest.xml` (`INTERNET`) | `releaseRuntimeClasspath` includes Google's Clearcut logging transport (`com.google.android.datatransport:transport-backend-cct` 2.3.3, pulled in by ML Kit), and the app holds `INTERNET` for translation downloads. | Before release, check Google's current ML Kit data disclosure and declare it in the Play Data safety form and the final policy. There is no supported switch to turn it off; removing `INTERNET` would break translation model downloads. |
| R24 | P2 | UX | `ui/LaunchIntro.kt`, `res/drawable/ic_launch_icon.xml`, `MainActivity.kt` | The new launch animation is verified by JVM renders compared frame by frame with the Figma render, `LaunchIntroTest` (timing, tap to skip) and `LaunchSmokeTest` (56 launches, Android 7-17), not on a device. | On a device: cold start from the launcher (Android 12+ and 11), app lock on, rotation during the animation, a share into the app (no animation). |
| R25 | P2 | UX | `scanner/PageQuality.kt` | The blur, glare and darkness limits come from the live detector and synthetic tests, not from real photos. | Run `SCAN_QUALITY_TEST_PLAN.md` photos through `OpenCvScanProcessor.assessQuality` and tune `GLARE_MIN_SHARE` / the clipped level. |
| R36 | P2 | Testing / UX | `feature/document/ScanSavedSheet.kt`, `scanner/PageQuality.kt`, `ui/LaunchIntro.kt` | Left over from the 2026-11-05 after-scan, page-quality and launch work; the suite and lint half is done (F80 run). | On a device scan a receipt, a business card and a letter and check the sheet; then R24/R25 device checks. |
| R42 | P3 | UX | `feature/library/LibraryScreen.kt` | Fix in (unsaved list state), awaiting a device check. | Confirm on the OnePlus; close R42 if good. |

Fixed items and their evidence are in the "Fixed" table of `AUDIT_FINDINGS.md`; build and test results in `BUILD_AND_TEST.md`.
