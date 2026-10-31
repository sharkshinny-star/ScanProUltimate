# ScanPro Ultimate architecture

Last updated: 2026-10-31, after commit `65d2c5c`. See also `AGENT_HANDOFF.md`, `BUILD_AND_TEST.md`, `AUDIT_FINDINGS.md`.

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

The primary scanner is `scanner/CameraScannerScreen.kt`.

### Live path

1. CameraX binds `Preview`, `ImageCapture`, and `ImageAnalysis` to the lifecycle.
2. Analyzer receives YUV frames and extracts the Y plane into a grayscale OpenCV `Mat`.
3. Frames are rotated into display orientation.
4. `DocumentEdgeDetector` bounds analysis to a long side of roughly 900 px.
5. Gaussian smoothing + adaptive Canny + morphology produce edge candidates.
6. Contours are approximated to convex quadrilaterals.
7. Candidates are scored by area, rectangularity, and opposite-edge balance.
8. Brightness and Laplacian variance supply low-light / blur guidance.
9. The stability tracker compares normalized corners over successive frames.
10. A stable, high-enough-confidence document can auto-capture.

### Final capture path

The preview quad is **not** blindly reused for export. A high-resolution image is saved first, then `OpenCvScanProcessor` detects the page again on a larger decoded image. Perspective correction uses that full-resolution second pass.

This two-stage design keeps preview analysis fast while protecting final scan quality.

## 3. Manual crop

`CropEditorDialog.kt` stores four normalized corners. The editor includes:

- draggable handles
- a magnified loupe while dragging
- reset
- undo / redo
- perspective correction on commit

Normalized coordinates allow the crop to survive preview-size changes and be reapplied to the retained source.

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
- luminance normalization
- CLAHE
- bilateral filtering
- restrained unsharp masking

Additional modes include enhanced grayscale, local adaptive B&W thresholding, and whiteboard cleanup. Manual tonal controls use a bounded color matrix after the named filter.

The adjustment dialog runs the same pipeline at a limited preview resolution and only applies full-resolution processing after confirmation.

## 6. OCR and indexing

`OcrEngine` wraps ML Kit Text Recognition for Latin, Devanagari, Chinese, Japanese, and Korean scripts. By default the
models come from Google Play services (downloaded on demand, smaller APK); `-Pscanpro.bundledMl=true` ships them inside the
APK instead (`BuildConfig.BUNDLED_ML`).

OCR text is persisted per page. `DocumentDatabase` mirrors OCR content into a Room FTS4 table for prefix full-text search. Search combines FTS hits with title/folder/tag metadata matches.

OCR can be edited manually and re-indexed. A document can also be re-OCR'd using another script.

## 7. PDF architecture

`PdfExporter` owns page layout and image compression. It supports Auto/A4/Letter page size, optional page numbers, and High/Balanced/Small image presets.

The current searchable PDF implementation provides a page text layer for searchability. Exact word-coordinate text geometry remains a documented future hardening task.

`PdfSecurity` uses PDFBox Android for password-protected exports. `PdfRenderer` is used to ingest existing PDFs into the editable image/OCR workflow.

## 8. Markup

Annotations are stored as normalized page objects rather than immediately overwriting the source:

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

The core library lives under app-private storage. Broad filesystem permission is not requested.

Export/import paths use Photo Picker, SAF, FileProvider and Android Sharesheet.

Security controls:

- optional biometric app gate
- PDF password protection
- encrypted `.scanpro` backups using AES-GCM
- PBKDF2-HMAC-SHA256 password derivation, 210k iterations, random salt and IV
- ZIP-slip/path traversal defense during backup restore
- Android cloud backup disabled for private scan files

A biometric UI gate should not be marketed as full at-rest encryption of every live library file. The encrypted portable-backup path is cryptographic; the main library relies on Android app-private storage unless a future per-document vault is added.

## 11. Background work

- `work/CleanupWorker` (daily, unique periodic): purges recycle-bin documents older than the user's retention setting,
  runs `CacheCleaner`, and posts the optional recycle-bin and backup reminders.
- `work/ImportScanWorker` (one-time, `@HiltWorker`): imports a finished camera session. The session lives in
  `filesDir/pro_scanner_session/<id>/` with a `.pending_import` marker; the worker retries up to 3 attempts. `CacheCleaner`
  removes abandoned sessions after a day, pending ones after 30 days.
- `core/operations/OperationRunner`: one long document job at a time on an application scope, promoted to
  `OperationForegroundService` (type `dataSync`) while running. `keepAlive(label) { }` lets other callers (PDF tools) hold the
  service. Queued jobs are in memory only (open item R3).

## 12. Notifications

`core/notifications/AppNotifications` (Hilt singleton) posts local notices only: finished/failed task while the app is out of
sight (`appVisible` is set by `MainActivity.onStart/onStop`), and optional reminders. Channels `results`, `failures`,
`reminders`; one fixed id per kind. Preferences in `SettingsStore` (`notify_finished`, `notify_failures` default on;
`notify_trash`, `notify_backup` default off). Taps use explicit immutable PendingIntents with `MainActivity.ACTION_OPEN_*`,
routed by `AppViewModel.destinationRequest` below the app-lock gate. No document names; neutral text when app lock is on.

## 13. Modules and dependencies

| Module | Purpose |
|---|---|
| `:app` | The application (`com.scanpro.app`), package `com.example.scanpro` |
| `:opencv` | Trimmed OpenCV build (Java wrappers + native libs), built by `scripts/opencv` |
| `:translate` | Play Feature Delivery on-demand module for ML Kit translation (built into the APK when `scanpro.sideload=true`) |
| `:baselineprofile` | Baseline profile generator |

Main libraries: Jetpack Compose (BOM 2026.09.00) + Material3, Navigation Compose 2.10.2, Hilt 2.60.1, Room 2.8.5 (FTS4,
schema v3, schemas in `app/schemas`), DataStore 1.3.0, WorkManager 2.12.0, CameraX, ML Kit (text recognition, barcode,
document scanner, translate, language-id), PDFBox-Android 2.0.27.0 with BouncyCastle forced to 1.86, coroutines 1.11.0.

## 14. Key decisions

- **Offline only.** No accounts, analytics, FCM or remote services; notifications are local.
- **Scans in `filesDir`, not `cacheDir`.** Android may clear the cache under storage pressure; unimported scans must survive.
- **Redaction is destructive in exports, reversible in backups.** Exports burn redaction in and rerun OCR; `.scanpro` backups
  keep originals for editability (open item R1).
- **Two release shapes.** Play bundle with an on-demand translate module, or per-ABI sideload APKs; Android forbids both at once.
- **Play-services ML by default** to keep the APK under the 40 MB arm64 budget; bundled models are opt-in.
- **Trimmed OpenCV** built from unmodified upstream sources; don't edit the generated Java wrappers.
- **BouncyCastle constraint** overrides PDFBox's vulnerable 1.72.

## 15. External-service extension points

Cloud sync, accounts, collaboration, subscription entitlements, and server AI are intentionally not mocked. They require real product decisions around authentication, backend storage, key management, privacy consent, billing products, conflict resolution and operational monitoring.
