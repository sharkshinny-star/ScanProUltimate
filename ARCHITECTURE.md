# ScanPro Ultimate architecture

## 1. Product architecture

The app is deliberately offline-first. Network access is not required for the core scan-to-PDF path.

```text
Compose UI
   ↓ events / StateFlow
MainViewModel
   ↓
DocumentRepository
   ├── Room + FTS
   ├── ImageProcessor / OpenCV
   ├── OcrEngine / ML Kit
   ├── PdfExporter / PdfSecurity
   ├── EncryptedDocumentPackage
   └── file-system document workspace
```

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

`OcrEngine` wraps bundled ML Kit Text Recognition models for Latin, Devanagari, Chinese, Japanese, and Korean scripts.

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

A unique periodic WorkManager task permanently removes documents that have remained in the recycle bin for 30 days.

Long OCR/PDF/edit operations run through repository suspend functions on `Dispatchers.IO` / background dispatchers rather than the main thread. Extremely large documents can be moved to a persistent WorkManager processing queue in a future production-hardening pass.

## 12. External-service extension points

Cloud sync, accounts, collaboration, subscription entitlements, and server AI are intentionally not mocked. They require real product decisions around authentication, backend storage, key management, privacy consent, billing products, conflict resolution and operational monitoring.
