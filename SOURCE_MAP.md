# Source map

## App / state

- `MainActivity.kt` — root activity, fallback ML Kit scanner launcher, Photo Picker, biometric gate
- `MainViewModel.kt` — StateFlow UI state and repository commands
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

## Compose UI

- `ui/ScanProApp.kt` — Home/Documents/Search/Settings navigation and library UI
- `ui/DocumentDetailScreen.kt` — document workspace and export/OCR/insight tools
- `ui/CropEditorDialog.kt` — four-corner crop + loupe + undo/redo
- `ui/AdjustmentsDialog.kt` — live low-res brightness/contrast/saturation preview
- `ui/SignaturePad.kt` — signature capture
- `ui/SettingsScreen.kt` — defaults, lock, storage and secure backup restore
- `ui/Components.kt` — thumbnails/dialogs/shared UI
- `ui/UiUtils.kt` — share/open/save/print utilities
- `ui/Theme.kt` — original light/dark fallback palette, shapes/typography, Material You dynamic color

## Tests

- `test/.../DocumentIntelligenceEngineTest.kt`
- `test/.../SmartTitleTest.kt`
- `test/.../CropQuadTest.kt`
- `test/.../PageAdjustmentsTest.kt`
- `androidTest/.../AppSmokeTest.kt`
