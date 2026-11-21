---
name: scanpro-scan-pipeline-qa
description: Debug, benchmark, and validate ScanPro's camera capture, native C++ OpenCV document detection, perspective correction, image processing pipeline, OCR/ML Kit, document intelligence, PDF generation, and background workers. Use when testing scanning quality, image transformation accuracy, threading, JNI memory handling, or offline reliability.
---

# ScanPro Scan Pipeline QA Skill

This skill provides testing, benchmarking, and validation protocols for **ScanPro Ultimate**'s core scanning engine, native image processing pipeline, machine learning, and document security.

## Core Inspection & Validation Areas

When validating or debugging the scanning and processing pipeline, focus on these 10 architectural domains:

### 1. CameraX Lifecycle & Frame Analysis (`scanner/CameraScannerScreen.kt`)
- Bindings: `Preview`, `ImageCapture`, `ImageAnalysis` bound cleanly to Activity lifecycle.
- Zero-copy preview binding and frame analysis execution off the main thread (`Dispatchers.Default` / Executor).
- Proper lifecycle disposal and resource cleanup (`unbindAll`, camera executor shutdown) preventing memory leaks or camera HAL freezes.

### 2. Coordinate Transformations & Quadrilateral Mapping
- Normalization of quadrilaterals (`CropQuad`) between preview YUV frame coordinates and high-res capture JPEG dimensions.
- Correct handling of EXIF orientation, display rotation, and sensor orientation across device turns.
- Multi-tier image downsampling (2000px, 1400px, 1000px) when loading large bitmaps for crop calculation or preview rendering.

### 3. Native OpenCV Processing & JNI Memory Safety (`:opencv`, `OpenCvScanProcessor.kt`)
- Native C++ `Mat` lifecycle management: explicit `mat.release()` calls in `finally` blocks to prevent native memory leaks.
- Canny edge detection, Gaussian blur, morphological ops, and convex contour approximation.
- Dual-pass detection: fast preview quad scoring $\rightarrow$ full-resolution re-detection on final capture commit.

### 4. Perspective Correction & Image Enhancement (`imaging/ImageProcessor.kt`)
- Warp perspective transformation mapping arbitrary 4-corner quads to clean rectangular page bounds.
- Enhancement filters: Original, Magic Color (LAB CLAHE + bilateral filtering), Greyscale, B&W local adaptive thresholding, Whiteboard cleanup.
- Manual tonal controls (brightness, contrast, saturation) applied non-destructively from cached `sourcePath`.

### 5. Page Quality Assessment (`scanner/PageQuality.kt`)
- Blur detection: Laplacian variance threshold check ($\text{variance} < 80$).
- Darkness check: Mean luminance threshold check ($\text{mean} < 45$).
- Glare detection: Solid clipped white patch area check ($1.5\% - 60\%$).

### 6. OCR & Text Recognition Pipeline (`data/OcrEngine.kt`, `data/DocumentDatabase.kt`)
- Google ML Kit Text Recognition integration (Latin, Devanagari, Chinese, Japanese, Korean).
- Asynchronous OCR text extraction and Room FTS4 full-text search database indexing.
- Standalone on-device capability with fallback when Play Services models are offline or bundled (`BuildConfig.BUNDLED_ML`).

### 7. Document Intelligence & Facts Extraction (`DocumentIntelligenceEngine.kt`)
- Offline document classification (Receipt, Business Card, Invoice, ID, Letter).
- Fact extraction: total amounts, dates, phone numbers, emails, addresses.
- Extractive passage Q&A without cloud LLM dependencies.

### 8. PDF Engine & Security (`imaging/PdfExporter.kt`, `imaging/PdfSecurity.kt`)
- PDFBox-Android page assembly, layout presets (Auto/A4/Letter), and image compression presets (High/Balanced/Small).
- PDF password encryption with BouncyCastle security constraints.
- Office to PDF conversion (`OfficeToHtml` $\rightarrow$ WebView $\rightarrow$ `ScanProPdfPrinter`).

### 9. Background Processing & WorkManager (`work/ImportScanWorker.kt`, `OperationRunner.kt`)
- `ImportScanWorker` execution for session import retry logic and `.import_request` handling.
- `OperationRunner` + `OperationForegroundService` promotion for long-running batch operations.
- Recoverable `EditJournal` transaction log replaying interrupted operations cleanly on app restart.

### 10. Offline Reliability & Failure Resilience (`TranslationFeature.kt`)
- First launch in airplane mode verification: dynamic feature installation failures gracefully report `UserFacingException` (`R.string.err_translation_network`).
- Core scanning, editing, PDF generation, and backup/restore functioning 100% offline.

---

## Validation Protocols & Test Execution

- **Unit & Pipeline Tests**: Run `./gradlew :app:testDebugUnitTest` to verify core processing engines (`ImageProcessorTest`, `ImageToolsEngineTest`, `PdfToolsFeaturesTest`, `CropQuadTest`).
- **Physical Device Tests**: Validate camera responsiveness, orientation changes, and frame rate on physical hardware.
- **Evidence Requirement**: Report exact test results, timing metrics, and memory bounds when verifying pipeline performance.
