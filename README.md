# ScanPro Ultimate 4.0

**ScanPro Ultimate** is an original, native Android document-scanner project built in Kotlin + Jetpack Compose. It is designed as a serious CamScanner-class product foundation without using CamScanner source code, branding, assets, or proprietary implementation details.

The core product is **offline-first**: camera analysis, perspective correction, enhancement, OCR, full-text indexing, PDF creation, local document intelligence, and library management can run on device. Google Play services document scanning remains available only as a compatibility fallback.

## What is implemented

### Custom pro scanner

- CameraX live preview and lifecycle binding
- OpenCV live document detection
- real-time four-corner overlay
- stability-based automatic capture
- manual shutter
- tap-to-focus
- torch
- zoom
- exposure compensation when supported by the device
- blur and low-light checks with capture guidance
- gallery import from Android Photo Picker
- batch / multi-page scanning
- Document, Receipt, Whiteboard, ID Card, and Book modes
- full-resolution second-pass edge detection and perspective correction
- Google ML Kit document scanner fallback for compatibility

### Precision crop + enhancement

- four-corner manual crop editor
- magnified corner loupe while dragging
- crop undo / redo / reset
- perspective correction
- 90° rotation
- Original, Auto Clean, Magic Color, Gray, adaptive B&W, Whiteboard filters
- OpenCV illumination normalization for uneven paper lighting
- CLAHE local contrast enhancement
- edge-preserving denoise
- restrained unsharp sharpening
- live low-resolution manual adjustment preview
- brightness / contrast / saturation controls
- non-destructive editing from retained source pages

### PDF workspace

- multi-page PDF generation
- searchable PDF export using OCR text
- High / Balanced / Small compression presets
- Auto / A4 / Letter page sizing
- optional page numbers
- drag/step page reordering
- duplicate / delete / extract pages
- split a book spread into two pages
- merge documents
- split a document into one-page documents
- import existing PDFs through Android `PdfRenderer` and convert pages into editable scans
- PDF sharing through Android Sharesheet
- save through Storage Access Framework
- Android printing
- password-protected PDF copies using AES-256 protection through PDFBox
- ID-copy sheet export
- long-image export
- JPEG sharing
- OCR text export as TXT

### Markup and signing

- hand-drawn signatures
- non-destructive signature overlays
- text annotations
- highlights
- redaction blocks
- watermarking
- undo last markup

### OCR + search

Bundled ML Kit Text Recognition models are configured for:

- Latin
- Devanagari
- Chinese
- Japanese
- Korean

Features include:

- asynchronous on-device OCR
- per-document OCR script selection and re-indexing
- editable recognized text
- copy / share / TXT export
- smart OCR-derived titles
- Room FTS-backed OCR search
- title / tag / folder search
- searchable PDFs

### Offline document intelligence

The built-in assistant is deliberately deterministic and source-grounded. It does not upload the scan or invent facts.

- classify invoice / receipt / contract / identity document / business card / letter / notes
- extract exact OCR amounts, dates, emails, phone numbers, and reference IDs
- generate extractive summaries
- identify potential action items from OCR text
- suggest tags
- answer simple questions by returning matching OCR passages
- optional ML Kit translation; a translation model may download once and then runs locally

### Document library

- Home / Documents / Search / Settings navigation
- grid and list layouts
- recent sorting
- sort by name / size / date
- folders and folder renaming
- tags
- favorites
- metadata and file sizes
- OCR full-text search
- multi-select
- bulk share
- bulk trash
- recycle bin
- restore / permanent delete / empty trash
- automatic cleanup for items left in trash for 30 days
- storage-usage summary

### Privacy + security

- no account required for scanning
- app-private local storage by default
- no broad storage permission
- Android Photo Picker / Storage Access Framework
- FileProvider sharing with temporary grants
- optional biometric app gate
- password-protected PDF exports
- AES-GCM encrypted `.scanpro` portable backups
- PBKDF2-HMAC-SHA256 key derivation with a per-backup salt
- safe ZIP extraction checks on restore
- Android backup disabled for private scan data by default

## Technology stack

- Kotlin 2.4.10
- Jetpack Compose + Material 3
- Clean-ish repository/MVVM separation with immutable models and StateFlow
- CameraX 1.6.2
- OpenCV 4.14.0
- Google ML Kit Text Recognition v2
- Google ML Kit document scanner fallback
- Room 2.8.5 + FTS4
- DataStore 1.2.1
- Hilt 2.60.1
- WorkManager 2.12.0
- Navigation Compose 2.10.2
- PDFBox Android 2.0.27.0
- Android `PdfDocument`, `PdfRenderer`, Print Framework, Photo Picker, SAF
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`
- Android Gradle Plugin 9.4.0 / Gradle 9.6.0 configuration
- JDK 17 source/target compatibility

## Architecture

```text
CameraX preview
    │
    ├── ImageAnalysis -> OpenCV low-res document detector
    │                    ├── quadrilateral score
    │                    ├── blur score
    │                    ├── brightness score
    │                    └── stability tracker -> auto capture
    │
    └── ImageCapture -> full-resolution JPEG
                         ↓
               full-res OpenCV re-detection
                         ↓
                 perspective correction
                         ↓
                  retained source page
                         ↓
      crop / filter / tonal adjustment / markup
                         ↓
                   rendered page JPEG
                   ├── ML Kit OCR
                   ├── Room + FTS index
                   └── PDF generation
```

Each document is persisted under app-private storage:

```text
files/documents/<document-id>/
├── document.pdf
├── source/       retained corrected originals
├── rendered/     current edited page renders
└── signatures/   signature assets used by overlays
```

See `ARCHITECTURE.md` and `SOURCE_MAP.md` for implementation details.

## Open in Android Studio

1. Install a current stable Android Studio that supports AGP 9.4.
2. Install Android SDK 37 and the required SDK/build tools.
3. Open this project folder.
4. If your checkout does not contain a Gradle wrapper JAR/scripts, generate them with a locally installed Gradle 9.6.x (`gradle wrapper --gradle-version 9.6.0`) or let Android Studio configure Gradle.
5. Sync Gradle.
6. Replace `com.example.scanpro` with a package ID you own.
7. Replace the `ScanPro Studio` app name and supplied placeholder vector icon with your final brand identity.
8. Run `testDebugUnitTest`.
9. Run the instrumentation tests on an emulator/physical device.
10. Test the camera pipeline on several physical Android devices before release.

## Build commands once the wrapper/toolchain is available

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew bundleRelease
```

A production release also needs your release signing configuration. Do not commit keystores or signing passwords.

## What is intentionally not faked

Some CamScanner-class features require external product infrastructure rather than believable-looking local placeholders. They are **not represented as complete** in this repository:

- automatic multi-device cloud sync backend
- account/authentication backend
- shared online workspaces/collaboration
- subscription entitlements / Google Play Billing product IDs
- server-side generative AI
- online Office-format conversion (DOCX/XLSX)
- remote abuse controls / quotas / operational dashboards

The local app is structured so these can be added behind explicit repositories/services after you choose a backend and privacy model. The core scan → edit → OCR → search → PDF workflow does not depend on them.

## Searchable PDF note

The current searchable exporter adds OCR text to PDF pages so text search works. It does **not** yet preserve every OCR word's exact original bounding box. For publication-grade text selection/highlighting geometry, store ML Kit element bounding boxes and place each word at its corresponding transformed coordinates during PDF export.

## Validation status

This generation environment has Kotlin/JVM but **does not have Android SDK, Gradle, Android Studio, emulator, or physical-camera hardware**. Therefore:

- portable Kotlin core checks were executed and passed
- Android XML parsing was executed and passed
- static source consistency checks were executed
- a full Android Gradle build was **not** executed here
- APK/AAB was **not** produced here
- CameraX/OpenCV behavior was **not** claimed as device-tested

See `BUILD_STATUS.md` for the exact status and first-device test plan.

## Product direction derived from current market research

CamScanner's current Play listing still centers on scanning/enhancement, OCR, sync, ID/book modes, e-signatures, collaboration, and cloud features. Recent public reviews also repeatedly criticize aggressive ads, subscription friction, and previously free features moving behind paywalls. ScanPro is therefore designed around a simpler privacy-first core with no forced account and no ad dependency in the codebase.

## Intellectual property

This project is original code. Do **not** publish it using CamScanner's name, icon, screenshots, copy, or a confusingly similar visual identity. Functional competition is fine; copying proprietary source/assets or passing off another product's branding is not.
