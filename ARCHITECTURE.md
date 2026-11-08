# ScanPro Ultimate architecture

Last updated: 2026-11-05, after the security hardening commit. See also `AGENT_HANDOFF.md`, `BUILD_AND_TEST.md`, `AUDIT_FINDINGS.md`.

## 1. Product architecture

The app is deliberately offline-first. Network access is not required for the core scan-to-PDF path.

```text
feature/<screen>/   Route (stateful)  →  Screen (stateless Compose UI)
        ↑ StateFlow            ↓ events
feature/<screen>/   <Screen>ViewModel   (one per screen, @HiltViewModel)
        ↓                                ↓
core/operations/OperationRunner   data/DocumentLibrary  (single source of truth)
        ↓                                ↓
                 data/DocumentRepository
                    ├── Room + FTS
                    ├── ImageProcessor / OpenCV
                    ├── OcrEngine / ML Kit
                    ├── PdfExporter / PdfSecurity
                    ├── EncryptedDocumentPackage
                    └── file-system document workspace
```

The app follows the layered, package-by-feature structure recommended in Android's architecture guide:

- **UI layer (`feature/*`, `navigation/`, `ui/`)**: each screen has a stateful `*Route` that collects
  its ViewModel's `StateFlow`s and a stateless `*Screen` that only takes values and callbacks, which
  keeps screens previewable and testable. `navigation/ScanProNavHost.kt` owns the graph, and route
  strings live in `ScanProDestinations`. `ui/ScanProApp.kt` is the app shell (lock gate, snackbar,
  share effects).
- **ViewModels**: `AppViewModel` (activity: theme, lock, imports, app-wide events), `LibraryViewModel`,
  `DocumentViewModel` (document id from `SavedStateHandle`), `TrashViewModel`, `SettingsViewModel`.
- **`core/`**: `designsystem` (theme, shared components), `ui` (share/format helpers), `operations`
  (`OperationRunner` runs one long document job at a time on an application-scoped coroutine, so jobs
  survive leaving the screen, and publishes progress, messages and exports), `di` (Hilt modules).
- **Data layer (`data/`)**: `DocumentLibrary` exposes the active, trash and storage flows every screen
  observes and is refreshed after each operation; `DocumentRepository` does the work.

Hilt supplies repository/database/settings/work dependencies. WorkManager is used for periodic library maintenance.

## 2. Scanner pipeline

The review screen ("Adjust page n") lets the user set a crop, a filter and brightness/contrast/saturation per page; all three travel with the photo URIs into `ImportScanWorker` (`KEY_CROPS`, `KEY_FILTERS`, `KEY_ADJUSTMENTS`) and are stored on the page by `DocumentRepository.importImages`, so the originals stay untouched and every choice can be changed later.

The primary scanner is `scanner/CameraScannerScreen.kt`.

### Live path

1. CameraX binds `Preview`, `ImageCapture`, and `ImageAnalysis` to the lifecycle.
2. Analyser receives YUV frames and extracts the Y plane into a greyscale OpenCV `Mat`.
3. Frames are rotated into display orientation.
4. `DocumentEdgeDetector` bounds analysis to a long side of roughly 900 px.
5. Gaussian smoothing + adaptive Canny + morphology produce edge candidates.
6. Contours are approximated to convex quadrilaterals.
7. Candidates are scored by area, rectangularity, and opposite-edge balance.
8. Brightness and Laplacian variance supply low-light / blur guidance.
9. The stability tracker compares normalised corners over successive frames.
10. A stable, high-enough-confidence document can auto-capture.

### Final capture path

The preview quad is **not** blindly reused for export. A high-resolution image is saved first, then `OpenCvScanProcessor` detects the page again on a larger decoded image. Perspective correction uses that full-resolution second pass.

This two-stage design keeps preview analysis fast while protecting final scan quality.

### Page quality

`scanner/PageQuality.kt` judges a page as blurred (variance of the Laplacian on the page < 80), too dark (mean grey
< 45) or glary (largest solid patch of clipped white covering 1.5-60 % of the page), with the same limits the live
detector uses. Live, `DocumentEdgeDetector` adds a glare hint (`Guidance.GLARE`) that never blocks auto-capture. After
capture, `CameraScannerScreen` runs `OpenCvScanProcessor.assessQuality` on every page in the background; the review
shows a notice with Retake above a flagged page and a mark on its thumbnail.

### After the scan

When a camera scan opens as a document, `DocumentScreen` shows `ScanSavedSheet` once: pages, size and a Searchable PDF
badge once text is read, the name, a smart action from `scanHighlight` (business card → Add to contacts via
`BusinessCardParser`; receipt/invoice with a total → Copy total via `DocumentIntelligenceEngine.total`), Share as PDF /
Copy text / Add pages, the other exports, and Scan another (camera, new document). All offline.

## 3. Manual crop

`CropEditorDialog.kt` stores four normalised corners. The editor includes:

- draggable handles
- a magnified loupe while dragging
- reset
- undo / redo
- perspective correction on commit

Normalised coordinates allow the crop to survive preview-size changes and be reapplied to the retained source.

## 4. Non-destructive page model

Each page keeps both:

- `sourcePath`: retained corrected/imported source
- `imagePath`: current rendered result

Edit state is metadata:

- crop quad
- filter
- manual rotation
- brightness / contrast / saturation
- annotation list

Changing a filter or adjustment re-renders from `sourcePath`; it does not repeatedly filter the already-filtered JPEG.

## 5. Enhancement

`ImageProcessor` contains the full-resolution and preview pipeline.

Auto enhancement uses:

- LAB luminance separation
- large-radius illumination estimation to suppress shadows
- luminance normalisation
- CLAHE
- bilateral filtering
- restrained unsharp masking

Additional modes include enhanced greyscale, local adaptive B&W thresholding, and whiteboard cleanup. Manual tonal controls use a bounded colour matrix after the named filter.

The adjustment dialog runs the same pipeline at a limited preview resolution and only applies full-resolution processing after confirmation.

## 6. OCR and indexing

`OcrEngine` wraps ML Kit Text Recognition for Latin, Devanagari, Chinese, Japanese, and Korean scripts. By default the
models come from Google Play services (downloaded on demand, smaller APK); `-Pscanpro.bundledMl=true` ships them inside the
APK instead (`BuildConfig.BUNDLED_ML`).

OCR text is persisted per page. `DocumentDatabase` mirrors OCR content into a Room FTS4 table for prefix full-text search. Search combines FTS hits with title/folder/tag metadata matches.

OCR can be edited manually and re-indexed. A document can also be re-OCR'd using another script.

## 7. PDF architecture

`PdfExporter` owns page layout and image compression. Callers can pass a `decode` hook (Images to PDF uses `decodePicture`: EXIF-upright, alpha composited on white) and a `beforePage` callback for progress and cancel. It supports Auto/A4/Letter page size, optional page numbers, and High/Balanced/Small image presets.

The current searchable PDF implementation provides a page text layer for searchability. Exact word-coordinate text geometry remains a documented future hardening task.

`PdfSecurity` uses PDFBox Android for password-protected exports. `PdfRenderer` is used to ingest existing PDFs into the editable image/OCR workflow.

**Office to PDF.** `imaging/OfficeToHtml` (pure JVM: DOCX/XLSX/CSV/text/HTML/MHTML → sanitised HTML with a CSP) → `imaging/HtmlToPdf` (offscreen WebView, scripts and network off) → `android.print.ScanProPdfPrinter` writes the print adapter's output to a file; fallback is the system print dialogue.

## 8. Markup

Annotations are stored as normalised page objects rather than immediately overwriting the source:

- text
- highlight
- redaction
- signature image

They are composited during render. Undo removes the last annotation and re-renders the page.

## 9. Document intelligence

`DocumentIntelligenceEngine` is source-grounded and offline. It returns exact OCR substrings for extracted facts and uses extractive passage selection for Q&A. It does not make unverifiable claims about a document.

The engine currently provides:

- document classification
- amounts / dates / contacts / reference extraction
- extractive summary
- potential action items
- suggested tags
- OCR-passage Q&A

`TranslationEngine` uses ML Kit translation. A language model can download once and then execute locally.

## 10. Storage and security

**Languages.** English plus Hindi, Tamil, Telugu, Kannada and Malayalam (`res/values-hi|ta|te|kn|ml`). `core/text/AppLocale` owns the in-app choice: `LocaleManager` on Android 13+, a saved tag applied in `attachBaseContext` of the application and activity below that. The bundle ships every language (language splits off).

**Locked folders.** `core/security/FolderLock` keeps locked folder names (DataStore `locked_folders`) and the folders opened this session (memory only). `LibraryViewModel` and `DocumentViewModel` read its `hidden` set; `LocalDeviceUnlock` (provided by `MainActivity`) shows the system prompt. A privacy screen, not encryption.

The core library lives under app-private storage. Broad filesystem permission is not requested.

Export/import paths use Photo Picker, SAF, FileProvider and Android Sharesheet.

Security controls:

- optional biometric app gate
- PDF password protection
- encrypted `.scanpro` backups using AES-GCM
- PBKDF2-HMAC-SHA256 password derivation, random salt and nonce prefix. New backups: 600k iterations and a password of
  at least 8 characters. Restore accepts 100k–1M iterations and 6 characters, so backups from earlier versions (210k,
  6 characters) still restore and older versions can read new backups.
- ZIP-slip/path traversal defence during backup restore, plus caps: 2 GB unpacked, 20,000 entries, 64 MB manifest, and
  the picked file copied with a size limit
- PDF password protection needs at least 8 characters
- Android cloud backup disabled for private scan files
- `MainActivity` has `android:taskAffinity=""`, so other apps cannot claim ScanPro's task affinity to insert screens into
  its task (task hijacking; Android 11+ is patched in the OS, on Android 7–10 this is a partial, app-side mitigation)
- every password field uses `KeyboardType.Password`, so keyboards do not learn or suggest passwords
- build supply chain: the Gradle wrapper pins the distribution's SHA-256 (`distributionSha256Sum`); `google()` is
  content-filtered to `com.android.*`, `com.google.*` and `androidx.*` in `settings.gradle.kts` (everything else comes
  from Maven Central; a new Google-hosted group needs adding to both filters); CI runs with a read-only token and no secrets

A biometric UI gate should not be marketed as full at-rest encryption of every live library file. The encrypted portable-backup path is cryptographic; the main library relies on Android app-private storage unless a future per-document vault is added.

## 11. Background work

`ImportScanWorker` takes an `ImportRequest` (photo URIs, mode, per-page crop/filter/sliders). It is saved as JSON in the scan's session folder; WorkManager gets the request itself when it fits its 10 KB input limit, otherwise only the file's path (`KEY_REQUEST_FILE`).

- `work/CleanupWorker` (daily, unique periodic): purges recycle-bin documents older than the user's retention setting,
  runs `CacheCleaner`, and posts the optional recycle-bin and backup reminders.
- `work/ImportScanWorker` (one-time, `@HiltWorker`): imports a finished camera session. The session lives in
  `filesDir/pro_scanner_session/<id>/` with a `.pending_import` marker; the worker retries up to 3 attempts. `CacheCleaner`
  removes abandoned sessions after a day, pending ones after 30 days. `enqueue` also writes the request (`.import_request`,
  WorkManager `Data` bytes) and tags the work `import-scan` + `import-scan-session:<id>`; a final failure writes `.import_failed`.
- `work/KeptScanStore` + `feature/kept/` ("Unsaved scans"): pending sessions with no active import, from
  `getWorkInfosByTagFlow`. Save again re-enqueues the recorded request; Discard deletes only after confirmation.
- `core/operations/OperationRunner`: one long document job at a time on an application scope, promoted to
  `OperationForegroundService` (type `dataSync`) while running. `keepAlive(label) { }` lets other callers (PDF tools) hold the
  service.
- **Edit journal** (`core/operations/EditJournal`, `feature/document/DocumentEdit`, `DocumentEditor`): document edits are
  data, not lambdas. `DocumentEditor.submit` writes `filesDir/edit_journal/<seq>.json` (on one serial IO thread), then
  queues the edit in `OperationRunner`. When its turn comes the file becomes `<seq>.started`; it is deleted when the edit
  finishes, fails or is stopped. `MainActivity.onCreate` calls `resumeInterrupted()` once per process: `.json` files run
  again in order, `.started` ones are only reported (never replayed: they may be half applied). Exports, password
  exports, signatures, pages from other apps, undo/redo and library bulk actions still run in memory only.

## 12. Notifications

`core/notifications/AppNotifications` (Hilt singleton) posts local notices only: finished/failed task while the app is out of
sight (`appVisible` is set by `MainActivity.onStart/onStop`), and optional reminders. Channels `results`, `failures`,
`reminders`; one fixed id per kind. Preferences in `SettingsStore` (`notify_finished`, `notify_failures` default on;
`notify_trash`, `notify_backup` default off). Taps use explicit immutable PendingIntents with `MainActivity.ACTION_OPEN_*`,
routed by `AppViewModel.destinationRequest` below the app-lock gate. No document names; neutral text when app lock is on.
Runtime permissions (camera, notifications on 13+) are asked once after the welcome on first launch (`StartupPermissions` in `ui/WelcomeScreen.kt`), then again in context if refused.

## 12b. Colour themes, Home tips, What's new

- **Colour themes.** `data/Models.kt` `AccentPalette` (Indigo, Ocean, Emerald, Sunset, Berry) is stored by name in
  `SettingsStore` (`accent_palette`; applied before the write lands, like the theme mode; picking one turns wallpaper colours
  off). `ScanProTheme(palette)` picks the Material scheme and the brand gradient (`paletteScheme`, `paletteBrand`). Indigo is
  the original hand-made scheme; the other four in `core/designsystem/Palettes.kt` are **generated** with Material Color
  Utilities (fidelity scheme, 2021 spec) from one seed each, so contrast pairs hold: regenerate from a seed rather than
  editing single roles. Brand gradients were picked for at least 4.5:1 white text. The mint scan line is the same in every
  theme because the scanner uses it to mean "page found". `PalettePicker` draws the swatches (Settings > Appearance).
  `MainActivity` holds the first frame until the palette has loaded (`AppViewModel.launchStateReady`) and `ThemeTransition`
  crossfades on any change of light/dark, palette or wallpaper colours.
- **Home tips.** `feature/library/HomeTips.kt`: a "Did you know?" card at the end of Home, one tip per day, each leading
  straight to its feature; closing it hides tips for good, Settings > Appearance brings them back (`home_tips`).
- **What's new.** `ui/WhatsNewSheet.kt`, shown once to people updating past `WHATS_NEW_VERSION` (`SettingsStore`), never with
  the welcome or the rating sheet; finishing the welcome marks it seen.
- **Launch animation.** Designed and assessed in Figma (https://www.figma.com/design/wlMBDKuD8B4ojE2RjQZWYi: frame
  `2:2` holds the keyframes, `6:2` the spec) and drawn by `ui/LaunchIntro.kt` from the same keyframe table: the icon is
  pressed, the scan line writes the page, the brackets lock on, an indigo-azure-teal flood fills the screen, "ScanPro"
  rises and holds, the icon zooms through into the app (the system launch screen now builds the icon and keeps its scan line moving while the app loads, `ic_launcher_splash.xml`; the in-app part starts at timeline 0.60 s, `START_AT`, so the build-up is not shown twice, and runs 1.65 s at 1x; a 3.0 s stretch felt stuck, 2026-11-08. The 2.25 s timeline is the Figma 1.6 s one stretched after the owner
  found it a blink; tap to skip). It is drawn in the app's own light/dark theme, and `MainActivity` passes that choice
  to the system (`UiModeManager.setApplicationNightMode`, Android 12+), so the system launch screen is in the same
  mode from the next start. The system launch screen shows its first frame
  (`res/drawable/ic_launch_icon.xml`: the icon tile, 144dp at the 288dp the system draws it, with a blank page), so
  the hand-over is invisible; when the animation plays the system screen just fades out in 120 ms. It plays only for a
  plain launcher start (not shortcuts, shares, notifications, Recents or a recreation), not with app lock on and not
  when system animations are off (`MainActivity.introWanted`). One clock drives it, read only while drawing.
- **Files sort direction and recent searches (R35/F76).** `LibrarySort` carries each key's default direction and
  its two direction labels; the DAO's paged queries take `ascending` and order with two CASE expressions, so the
  list stays a database page in either direction; `SettingsStore.librarySortAscending` (null = the key's default)
  and `recentSearches` (JSON list, `RecentSearches.add` keeps eight, newest first, no repeats) back the UI. Tags (F77):
  `LibraryOverview.tags` lists every tag in use (from `tags_json`), the paged queries take a `tag` matched as a whole
  quoted JSON string, and `TagsSheet` / `TagFilterSheet` edit and filter by them.
- **Duplicate-aware auto-capture (R32/F75).** `scanner/PageSignature.kt`: `PageSignature.of` shrinks the page inside
  its outline to 32 x 32 and normalises brightness and contrast; `isSamePage` compares mean difference against 0.22.
  `OpenCvDocumentAnalyzer` keeps the signature of each auto-capture and, on a would-be capture, skips a matching
  page (guidance `SAME_PAGE`); `SharpnessGate` makes it wait for a frame about as sharp as the recent steady ones.
- **Share PDF card (R28/F73).** `imaging/PdfShare.kt` holds the maths: `extrapolate` (two sample pages rendered
  through `PdfExporter`, scaled to the page count), `pickQuality` (best quality under a size target, else Small) and
  `shrinkPlan` (lower qualities, then Small at 1000/800/600 px) with `build` running the plan until the file fits.
  `DocumentRepository.estimatePdfSizes`/`exportPdfSelection` wire it; `SharePdfSheet` (opened from the Export sheet's
  PDF row) shows page ticks, the three qualities with "≈ size", and the Keep-under chips.
- **Fast save from the camera (R26/F68).** `data/ReviewMode` (Settings › Scanning › Check pages before saving; default
  If needed) reaches `CameraScannerScreen` through `AppViewModel`/`ScanProNavHost`. `scanner/FastSave.kt` holds the
  rule: a page needs a look when its crop is the whole frame (no outline found) or the F64 quality check flagged it.
  `reviewOrSave()` runs on Done: `FastSave.reviewWithoutChecking` (Always, a retake of a saved page, or the review
  was left via Add page / Back) opens the review at once; otherwise missing crop and quality results are worked out
  from one decode per page (`OpenCvScanProcessor.inspect`) and stored, the composed ID page is trusted, and it either
  calls `saveReviewed()` or opens the review on the first page that needs a look.
- **ID card: both sides on one page.** `scanner/IdCardComposer.kt` cuts each side out of its photo along the guide
  crop (`ImageProcessor.cropSourceToNewFile`), scales both to the same width and places them on a white A4-shaped
  page, front above back, with a hairline border (`ImageProcessor.cropSourceToBitmap`: one decode per side, no temporary
  file). The camera does this when Done is tapped after the second side
  (`CameraScannerScreen.finishScan`), replacing the two captures with the one page, so the review, the filters and
  the saved document all see a single page. Retake on that page clears it and starts both sides again.
- **Instrumented tests** (`app/src/androidTest`): `AppSmokeTest` plus, from 2026-11-08, device tests for the launch
  (`LaunchDeviceTest`), the in-place language change (`LanguageChangeDeviceTest`) and ID cards (`IdCardDeviceTest`),
  all on `createEmptyComposeRule` + `ActivityScenario`, no extra dependencies (the camera permission is granted
  through `UiAutomation`). They run from Android Studio on a phone or emulator; CI does not run them.
- **Language and theme changes without a restart.** `MainActivity` declares `configChanges="locale|layoutDirection|uiMode"`:
  the Android 13+ per-app language change and the per-app night mode arrive as `onConfigurationChanged` (Compose re-reads
  `LocalConfiguration`; launcher shortcuts are republished) instead of recreating the activity, which showed a black
  frame after the language picker. Android 12 and older still recreate in `AppLocale.apply`.
  `MainActivity` settles the app from 104 % at the reveal; `ScanProApp(launchSettled)` keeps the rating and What's new
  sheets back until it ends, and the crash prompt waits too. It replaced the animated splash icon (`ic_launcher_splash`).

## 13. Modules and dependencies

All dependency coordinates live in `gradle/libs.versions.toml` (F71, 2026-11-08): `:app` uses `libs.*` accessors with shared `[versions]` refs for Compose BOM, CameraX, Room, Hilt, ML Kit and the test libraries, like `:translate` and `:baselineprofile` before it; lint's `UseTomlInstead` is on.

| Module | Purpose |
|---|---|
| `:app` | The application (`com.scanpro.app`), package `com.example.scanpro` |
| `:opencv` | Trimmed OpenCV build (Java wrappers + native libs), built by `scripts/opencv` |
| `:translate` | Play Feature Delivery on-demand module for ML Kit translation (built into the APK when `scanpro.sideload=true`) |
| `:baselineprofile` | Baseline profile generator |

Main libraries: Jetpack Compose (BOM 2026.09.00) + Material3, Navigation Compose 2.10.2, Hilt 2.60.1, Room 2.8.5 (FTS4,
schema v3, schemas in `app/schemas`), DataStore 1.3.0, WorkManager 2.12.0, CameraX, ML Kit (text recognition 19.0.2 / scripts 16.0.2, barcode,
document scanner, translate, language-id), PDFBox-Android 2.0.27.0 with BouncyCastle forced to 1.86, coroutines 1.11.0.

**Tests.** JVM unit tests plus `LaunchSmokeTest` (Robolectric, test-only dependency) that starts the app on every supported Android version, API 24–37. `ScreenScreenshotTest` (opt-in, `-Pscanpro.screenshots=true`) renders the stateless screen composables with sample data under Robolectric native graphics and a plain `Application` (no Hilt), for design review in Figma without an emulator; it adds Compose `ui-test-junit4` to the test classpath only. Camera, PDF viewer and PDF tools are not covered.

## 14. Key decisions

- **Offline only.** No accounts, analytics, FCM or remote services; notifications are local.
- **Scans in `filesDir`, not `cacheDir`.** Android may clear the cache under storage pressure; unimported scans must survive.
- **Redaction is destructive in exports, reversible in backups.** Exports burn redaction in and rerun OCR; `.scanpro` backups
  keep originals for editability (open item R1).
- **Two release shapes.** Play bundle with an on-demand translate module, or per-ABI sideload APKs; Android forbids both at once.
- **Play-services ML by default** to keep the APK under the 40 MB arm64 budget; bundled models are opt-in.
- **Trimmed OpenCV** built from unmodified upstream sources; don't edit the generated Java wrappers.
- **BouncyCastle constraint** overrides PDFBox's vulnerable 1.72.
- **targetSdk 37** (Android 17): no orientation or aspect-ratio locks, no reflection on platform internals, no background `IntentSender` launches; app lock relies on `FLAG_SECURE`, which also blocks Content Capture.
- **Localised errors:** user-visible errors are `core/text/UserFacingException` (an `IllegalArgumentException`) carrying a string
  resource; `message` resolves through `AppStrings` (application context, set in `ScanProApplication.onCreate`) each time it is
  read, so engines without a Context can throw translated errors and existing `error.message` call sites need no change.
- **Shared IDE settings** (`.idea/scopes`, `.idea/inspectionProfiles`, `.idea/dictionaries`) are committed; the rest of `.idea/` stays ignored.

## 15. External-service extension points

Cloud sync, accounts, collaboration, subscription entitlements, and server AI are intentionally not mocked. They require real product decisions around authentication, backend storage, key management, privacy consent, billing products, conflict resolution and operational monitoring.
