# Remaining work

Everything still open on `claude/awesome-clarke-ussjo3`, as of 2026-11-05. Generated from the "Open" table in
`AUDIT_FINDINGS.md`, which holds the full problem description and evidence for each ID; keep that table as the source
of truth and update this list when items close.

## Before release: device checklist

Nothing below has run on a phone; every check so far is a JVM test or render.

1. Run the full `./gradlew :app:testDebugUnitTest :app:lintDebug` (not re-run after F65, see R36), then the release builds once.
2. Launch animation (R24): cold start from the launcher on Android 12+ and on Android 11; with app lock on (no animation);
   rotate during it; share a file into the app (no animation).
3. Page-quality check (R25): blurred, glary and dark pages flagged in review with Retake; glossy covers, white-on-white
   pages and IDs with holograms not over-flagged; glare hint in the camera never blocks auto-capture.
4. "Scan saved" sheet (R36): a receipt (total + Copy total), a business card (Add to contacts), a letter (no smart action);
   the Searchable PDF badge and Copy text appear once text recognition finishes.
5. Colour themes, Home tips and What's new (R19) in light and dark; tablet side rail Scan button (F52).
6. Android 17 / targetSdk 37 on a phone and a tablet or foldable (R2).
7. Native-speaker check of the Hindi, Telugu, Kannada, Tamil and Malayalam strings added for F64, F65 and the themes (R19, R36).

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
| R26 | P2 | UX | `scanner/CameraScannerScreen.kt`, `scanner/ScanReviewScreen.kt`, `data/SettingsStore.kt` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | Settings › Scanning › "Check pages before saving": Always (today, default until the owner decides), Only pages that need it (no page found, blurry), Never. Needs detectCrop to return confidence and a blur score. |
| R27 | P2 | UX | `scanner/ScanReviewScreen.kt`, `imaging/ImageProcessor.kt` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | Two stage states: Result (default, the page rendered cropped and filtered at about 1400 px) and Crop (today's handles), switched by a Crop tool; a labelled tool row (Crop, Filters, Adjust, Rotate, Retake). |
| R28 | P2 | UX | `feature/document/DocumentScreen.kt` (Export sheet), `imaging/PdfExporter.kt` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | One "Share PDF" card: page ticks (all by default), High / Balanced / Small each labelled with an estimated size, an optional size target, password; estimates from a low-resolution trial render. |
| R29 | P2 | UX / Imaging | `scanner/CameraScannerScreen.kt` (Book mode), `feature/document/BookSplitDialog.kt` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | In Book mode, a Two pages / One page row; with Two pages, a dashed spine line through the live outline and each capture split at it into two pages (left page first, switchable). |
| R36 | P2 | Testing / UX | `feature/document/ScanSavedSheet.kt`, `scanner/PageQuality.kt`, `ui/LaunchIntro.kt` | Left over from the 2026-11-05 after-scan, page-quality and launch work: | Run the full suite and lint; on a device scan a receipt, a business card and a letter and check the sheet; then R24/R25 device checks. |
| R9 | P3 | Maintainability | `data/DocumentRepository.kt` (~1,500 lines) | One class does most data work. | Split by use case. |
| R10 | P3 | Build | `app/build.gradle.kts` | 49 `UseTomlInstead` lint notes since the owner's version catalog arrived; | Move `:app`'s inline dependencies into `gradle/libs.versions.toml`. |
| R17 | P3 | UX / Accessibility | `core/designsystem/Components.kt`, hard-coded colours (`RatingSheet`, `FilterStrip`, `FavouriteGold`) | Left over after F40 (drag-to-reorder done in F44): | Drag-to-reorder on the strip; measure contrast on a device in both themes. |
| R18 | P3 | UX | `feature/trash/TrashScreen.kt`, `feature/document/DocumentScreen.kt`, `feature/settings/SettingsScreen.kt`, `feature/library/DocumentCards.kt` | From the screenshot review (360 dp phone), not changed (the Recycle bin actions were moved under the notice in F43): | Owner picks; Empty into ⋮ is the cheapest. |
| R21 | P3 | Security | `MainActivity.handleIntent` | The exported activity accepts the internal actions (open document, recycle bin, settings, unsaved scans, scan) from any app. | Optional: route notification and shortcut actions through a non-exported trampoline activity. |
| R22 | P3 | Privacy | `core/crash/CrashReporter.kt` | Crash reports include exception messages, which can hold file names derived from document titles. | Optional: replace paths under `filesDir`/`cacheDir` with placeholders before writing. |
| R30 | P3 | UX | `scanner/CameraScannerScreen.kt` (`CapturedStrip`) | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | Draw each thumbnail cropped and filtered (160 px, redrawn when the background outline arrives), long-press for Delete with undo, an amber mark on pages with no page found or a blurry shot. |
| R31 | P3 | UX | `scanner/CameraScannerScreen.kt` (top bar) | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | One top-bar flash button cycling Off, Auto, On, Torch (the low-light hint sets Torch through it), remembered between scans. Owner's design moved flash into ⋮ (F30), so this needs the owner's decision. |
| R32 | P3 | UX | `scanner/OpenCvDocumentAnalyzer.kt` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | Keep a 32x32 greyscale signature of each captured page; re-arm only when the page has changed, and capture the sharpest of the last few steady frames. |
| R33 | P3 | UX | manifest, new `TileService` / widget | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | A Quick Settings "Scan" tile (TileService) and a 2x1 RemoteViews widget (Scan, ID card, Import), no new dependency. |
| R34 | P3 | UX / i18n | `imaging/SmartTitle.kt` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | Give SmartTitle a string provider with the document-kind labels in all six languages; optional name pattern in Settings (mode, date, first line). |
| R35 | P3 | UX | `feature/library/*` | Competitor comparison 2026-11-04 (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan; | A Tags sheet with chips, a Tags filter chip in Files, sort direction and Date created, and recent searches on the Search screen. |

Fixed items and their evidence are in the "Fixed" table of `AUDIT_FINDINGS.md`; build and test results in `BUILD_AND_TEST.md`.
