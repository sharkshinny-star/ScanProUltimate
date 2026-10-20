# Source map

## App / state

- `MainActivity.kt` — root activity, fallback ML Kit scanner launcher, Photo Picker, biometric gate
- `AppViewModel.kt` — activity-level state: theme, app lock, imports, app-wide messages/exports
- `ScanProApplication.kt` — Hilt, PDFBox/OpenCV initialization, WorkManager setup

## Camera / CV

- `scanner/CameraScannerScreen.kt` — CameraX preview/capture, torch, zoom, exposure, tap-focus, batch mode, live overlay
- `scanner/OpenCvDocumentAnalyzer.kt` — YUV grayscale conversion, frame analysis, stability-based auto capture
- `scanner/DocumentEdgeDetector.kt` — OpenCV edge/contour/quadrilateral detection, blur/light quality signals
- `scanner/OpenCvScanProcessor.kt` — full-resolution re-detection and perspective correction after capture

## Data

- `data/Models.kt` — immutable document/page/crop/adjustment/annotation models
- `data/DocumentDatabase.kt` — Room entities/DAO/FTS-backed OCR search
- `data/DocumentRepository.kt` — scan/import/edit/OCR/PDF/library orchestration
- `data/JsonCodec.kt` — compact JSON persistence for lists/crop/adjustments/annotations
- `data/SettingsStore.kt` — DataStore preferences

## Image / PDF

- `imaging/ImageProcessor.kt` — perspective transform, enhancement filters, manual tonal controls, annotations
- `imaging/OcrEngine.kt` — ML Kit OCR for five script families
- `imaging/PdfExporter.kt` — multi-page/searchable PDF output and quality/page-size controls
- `imaging/PdfSecurity.kt` — PDFBox password protection/unlock helpers
- `imaging/SpecialExporters.kt` — ID-copy and long-image outputs
- `imaging/SmartTitle.kt` — OCR-derived local titles

## Intelligence / language

- `intelligence/DocumentIntelligenceEngine.kt` — offline classification, exact fact extraction, summaries, actions, tags, grounded Q&A
- `intelligence/TranslationEngine.kt` — language identification + ML Kit translation

## Security / portability

- `backup/EncryptedDocumentPackage.kt` — password-encrypted `.scanpro` AES-GCM backup/restore
- `work/CleanupWorker.kt` — recycle-bin expiry maintenance

## Core

- `core/di/CoroutineScopeModule.kt` — application-scoped coroutine scope
- `core/operations/OperationRunner.kt` — one-at-a-time document jobs, progress, messages, exports
- `core/designsystem/Theme.kt` — ScanPro palette, shapes, typography, light/dark/system theme
- `core/designsystem/Components.kt` — cached page thumbnails and shared dialogs
- `core/ui/UiUtils.kt` — share/open/save/print and formatting helpers
- `data/DocumentLibrary.kt` — observable document, trash and storage state shared by all screens

## Features (each: `*Route` + stateless `*Screen` + `*ViewModel`)

- `feature/library/` — Home dashboard (recent documents), Files tab and the Search screen (`LibraryScreen.kt`, `DocumentCards.kt`)
- `feature/document/` — document workspace (`DocumentScreen.kt`), crop editor, adjustments, signature pad
- `feature/trash/` — recycle bin with 7-day countdown
- `feature/settings/` — defaults, theme, app lock, storage
- `feature/lock/` — app lock screen

## Navigation / shell

- `navigation/ScanProDestinations.kt` — route strings and arguments
- `navigation/ScanProNavHost.kt` — navigation graph and tab navigation
- `navigation/ScanProBottomBar.kt` — bottom navigation bar (Home, Files, Settings)
- `ui/ScanProApp.kt` — app shell: lock gate, snackbar, share and open-document effects

## Tests

- `test/.../DocumentIntelligenceEngineTest.kt`
- `test/.../SmartTitleTest.kt`
- `test/.../CropQuadTest.kt`
- `test/.../PageAdjustmentsTest.kt`
- `androidTest/.../AppSmokeTest.kt`
