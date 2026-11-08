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

## Owner decisions

- **Screenshots in debug builds**: `MainActivity` skips `FLAG_SECURE` when `BuildConfig.DEBUG`, so debug builds can be
  screen-recorded while locked. Release builds are unaffected. Keep, or remove before release.
- **R18, R20, R21, R22, R23**: see the table below; each needs a choice before work starts.
- R9 (splitting `DocumentRepository`) is done (F81).

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
| R20 | P2 | Security | whole app, `MainActivity.authenticate`, `core/security/FolderLock.kt` | Documents, OCR text and the database are not encrypted by the app; | Owner decision (architectural, document first): encrypt page files and the database with an Android Keystore key (optionally auth-bound), e.g. Tink streaming AEAD plus SQLCipher. Costs speed and adds a key-loss risk. |
| R23 | P2 | Privacy / compliance | ML Kit dependencies, `AndroidManifest.xml` (`INTERNET`) | `releaseRuntimeClasspath` includes Google's Clearcut logging transport (`com.google.android.datatransport:transport-backend-cct` 2.3.3, pulled in by ML Kit), and the app holds `INTERNET` for translation downloads. | Before release, check Google's current ML Kit data disclosure and declare it in the Play Data safety form and the final policy. There is no supported switch to turn it off; removing `INTERNET` would break translation model downloads. |
| R24 | P2 | UX | `ui/LaunchIntro.kt`, `res/drawable/ic_launch_icon.xml`, `MainActivity.kt` | The new launch animation is verified by JVM renders compared frame by frame with the Figma render, `LaunchIntroTest` (timing, tap to skip) and `LaunchSmokeTest` (56 launches, Android 7-17), not on a device. | On a device: cold start from the launcher (Android 12+ and 11), app lock on, rotation during the animation, a share into the app (no animation). |
| R25 | P2 | UX | `scanner/PageQuality.kt` | The blur, glare and darkness limits come from the live detector and synthetic tests, not from real photos. | Run `SCAN_QUALITY_TEST_PLAN.md` photos through `OpenCvScanProcessor.assessQuality` and tune `GLARE_MIN_SHARE` / the clipped level. |
| R36 | P2 | Testing / UX | `feature/document/ScanSavedSheet.kt`, `scanner/PageQuality.kt`, `ui/LaunchIntro.kt` | Left over from the 2026-11-05 after-scan, page-quality and launch work; the suite and lint half is done (F80 run). | On a device scan a receipt, a business card and a letter and check the sheet; then R24/R25 device checks. |
| R42 | P3 | UX | `feature/library/LibraryScreen.kt` | Fix in (unsaved list state), awaiting a device check. | Confirm on the OnePlus; close R42 if good. |
| R18 | P3 | UX | `feature/trash/TrashScreen.kt`, `feature/document/DocumentScreen.kt`, `feature/settings/SettingsScreen.kt`, `feature/library/DocumentCards.kt` | From the screenshot review (360 dp phone), not changed (the Recycle bin actions were moved under the notice in F43): | Owner picks; Empty into ⋮ is the cheapest. |
| R21 | P3 | Security | `MainActivity.handleIntent` | The exported activity accepts the internal actions (open document, recycle bin, settings, unsaved scans, scan) from any app. | Optional: route notification and shortcut actions through a non-exported trampoline activity. |
| R22 | P3 | Privacy | `core/crash/CrashReporter.kt` | Crash reports include exception messages, which can hold file names derived from document titles. | Optional: replace paths under `filesDir`/`cacheDir` with placeholders before writing. |

Fixed items and their evidence are in the "Fixed" table of `AUDIT_FINDINGS.md`; build and test results in `BUILD_AND_TEST.md`.
