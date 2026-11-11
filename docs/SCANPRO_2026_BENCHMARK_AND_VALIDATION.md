# ScanPro engineering, competitor and release validation

Date: 12 November 2026. **Evidence levels matter:** the public source and product documentation were reviewed; the APKs installed on the owner's phone were not accessible in this environment. The patch was written against publicly inspected source fragments and validated on reconstructed anchors, not cloned Git history. It is an **engineering increment**, not a production certification or a claim of superior image quality.

## Observed competitor journeys (published sources, NOT phone tests)

| Step | Adobe Scan (official public description) | iLovePDF (official public description) | ScanPro differentiator |
| --- | --- | --- | --- |
| Open / discover | Camera-centric capture, manual/auto capture | Tools and document manager | Prominent scan action with searchable, task-based PDF workspace |
| Detect/capture | Four-corner live boundaries, stability messaging, auto/manual | Multi-page capture and adjustable corners | Auto capture with non-obstructing blur/glare/tilt indicators; manual always available |
| Review/edit | Crop handles, straighten/curve, filters, rotate, add/reorder | Page/document management, annotations | Keep current non-destructive retained originals and preview-first review |
| OCR | Searchable text, added languages and curvature improvements in 2025–26 | OCR from scanner and PDFs | Offline on-device recognition with inspectable and editable OCR text |
| Export | PDF/JPEG and cloud integration | Compression, signing, conversion, device/cloud file organisation | Private-by-default local PDF + explicit Save/Share, never silently overwrite source |

Sources:
- https://www.adobe.com/devnet-docs/adobescan/android/en/scan.html
- https://www.adobe.com/devnet-docs/adobescan/android/en/releasenotes.html
- https://www.adobe.com/devnet-docs/adobescan/android/en/export.html
- https://www.ilovepdf.com/mobile
- https://www.ilovepdf.com/blog/how-to-scan-document-on-android
- Public Google Play listings: `com.adobe.scan.android`, `com.ilovepdf.www`.

**User-report caveat:** published iLovePDF reviews describe friction when a tool must be chosen before editing a PDF and concerns about original-file replacement. These are user reports, not independently reproduced defects. Adobe subscriptions/cloud workflows attract mixed reviews; no claim is made that all users encounter these issues. No proprietary implementation or imagery was copied.

## Source audit findings (not a full manual dynamic audit)

1. **Privacy, P1:** `ScanHistoryStore` persisted complete scanned QR values, including Wi-Fi passwords, as plain JSON in internal storage. Android app sandboxing limits access but does not encrypt these values at rest. **Patch:** Keystore-backed AES-GCM, legacy migration, tamper/incorrect-key handling, status in the history screen, exclusion from device transfer. Backup/recovery of QR history across devices is intentionally unsupported; all ordinary library documents retain the project's existing transfer rules.
2. **Accuracy, P1:** `DocumentEdgeDetector` used a rectangular ROI for glare and frame-wide brightness for the capture hint; a white desk outside a skewed page or a dark desk around a well-lit page can skew the result. **Patch:** page polygon for glare and page ROI for brightness. Real glossy pages and angle changes still require tuning.
3. **Efficiency, P2:** temporary OpenCV contour objects were created around multiple quality checks in the same frame. **Patch:** one bounding rect reused; objects released. No empirical frame-time claim.
4. **Usability, P2:** tool actions exist but can be hard to discover by task. **Patch:** search, aliases, categories, local favourites/recents, descriptive cards, clearer result actions, larger touch targets. All existing tool ViewModels and engines are retained.
5. **Quality ceiling:** source already supports non-destructive pages, OpenCV filters, multi-page OCR, PDF, signed PDFs, book/ID modes and photo import. Don't replace working components blindly. Missing: validated shadow/glare calibration, true dewarping for curved books, exact OCR word coordinates, complete Android-device test coverage.

## End-to-end workflow design

Open → **Scan** → live guide → auto or manual capture → crop/filter only when needed → page strip → **Save** → local preview → Share or Save to another folder. The user can retake at every capture point. PDF tools are also accessible from a selected document when the current feature supports it; tool discovery remembers local favourites but never uploads usage or files. Permission denial keeps manual photo import accessible.

Design rules: Material 3 adaptable components; one dominant action per stage; 48 dp touch targets; clear status and no destructive hidden actions; TalkBack announcements should not repeat on each analysis frame; colour is not the sole status cue; test at 320/360/600 dp and up to 200% text. Use existing theme tokens and typography; do not copy Adobe or iLovePDF assets.

## Scanner algorithm and quality strategy

1. Keep CameraX YUV analysis off the main thread; cap analysis image to ~900 px for steady preview, always run full-resolution second-pass detection on captured JPEG.
2. Prefer quadrilateral confidence, angle score, area and temporal stability; user can always override with manual corners. Detection under complex backgrounds should use contour retrieval and candidate scoring already present.
3. Limit brightness and glare evaluation to the candidate document. For strong reflection, ask the user to change angle rather than fabricate missing text. Do not apply hallucinated detail recovery or aggressive sharpening.
4. For colour, reuse LAB illumination/CLAHE/edge-preserving denoise with restrained unsharp; preserve Originals and mark filtered results as reversible. For receipts and text-only scans compare grayscale/B&W with original readability before export.
5. Keep bundled/offline ML Kit OCR under explicit language selection; accuracy requires matched ground-truth text. For searchable PDF, store per-word OCR bounding boxes and crop/perspective transform before claiming accurate selection overlays.
6. Evaluate optional GPU paths only after profiling common devices and measuring thermal, battery, size and licence impact. No unmeasured "AI enhancement" claim.

### Reproducible benchmark protocol (not performed yet)

Capture each scene **three times on the same phone** with Adobe Scan, iLovePDF and ScanPro; same lighting, distance, flash, camera lens and page, record manual/auto behavior separately:

| Scene | Ground truth and scoring |
| --- | --- |
| A4 printed text | Corner error in % diagonal, full words preserved, OCR character error rate |
| 6-point / 8-point text | Legible glyph count, OCR character/word error rate |
| Narrow thermal receipt | Crop clipping, white-background preservation, totals OCR |
| Handwriting | Original strokes retained, no invented/removed edges |
| Curved book spread | Page curve distortion and missing boundary failure rate |
| Low light | Blur warnings, exposure, readability and retake rate |
| Glossy card/glare | False/true glare warnings; readable text remains authentic |
| Shadow across page | Text legibility and white balance across the page |
| Severe perspective / 30° angle | Document corners and transformed line straightness |
| Patterned background | False page detection, crop confidence and manual override |
| 10-page batch | Capture-to-save median/P95, peak memory, missing/duplicate pages, PDF size |

**Metrics**: auto-detect recall/precision on annotated quads; corner error as mean corner distance / page diagonal; glare precision/recall on labeled photo polygons; OCR CER/WER using a fixed reference transcript; PDF effective resolution, file size and text selection; median/P95 latency and janky-frame percentage (Perfetto/JankStats); peak PSS, battery via Battery Historian and sustained-camera thermals. Test 3 mid-range and 1 high-end Android phones, versions 9/12/15/17 where available. Record phones/OS, app versions, optics, failures, and sample counts. Do not claim victory until statistically justified.

**Initial engineering budgets, targets not measured:** camera ready under 1.2 s warm, capture-to-review P95 under 1.5 s for A4 on a calibrated mid-range target, document-detection analysis P95 under 40 ms at the chosen frame-skip setting, smooth 60 Hz Compose gestures where supported, no OOM with 20-page documents. Revise budgets after observing realistic baseline.

## Patch scope and tests

Implemented in the single patch:
- Local PDF discovery design, search aliases, favourites/recents, consistent processing and result presentation, improved accessibility touch targets.
- Scanner quality indicators, angle guidance, document-polygon glare checks and page-area exposure correction; manual capture continues to work.
- Encrypted QR history and migration; wrong-key/tamper-safe failure path and a warning surface when storage becomes unavailable.
- Unit tests for glare, alignment, search and AES-GCM/history migration; Android backup rule updated to avoid transferring ciphertext without the Keystore key.

What **has not** been completed in this environment: full project checkout/build, instrumentation, verified camera frame rate, OCR/CER benchmarks, competitor phone tests, visual screenshot goldens and accessibility sweep, release APK signing. See `BUILD_AND_TEST.md` for the repository's previous CI evidence, which is **not evidence for this new patch**.

## Acceptance / release gate

1. On the maintained branch, `git apply --check` then `git apply` this patch. Do not stack it with the older UI/scanner patches.
2. Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` locally; resolve failures.
3. Run `./gradlew :app:connectedDebugAndroidTest` on a phone/emulator. Test scanner permission denied/permanently denied, background/resume, battery saver, auto/manual capture and 10-page batching.
4. Reproduce PDF Merge/Split/Organise/Rotate/Extract/Compress/Sign/Images to PDF/Office to PDF/OCR/Forms/PDF to images/Text/Protect/Unlock/Viewer with small/large/password-protected PDFs, cancellation, invalid files and SAF saves; confirm originals remain intact.
5. Run QR history on Android 7/12/15/17: verify plaintext-to-encrypted migration on upgrade; key-loss state shows a warning; wrong-key cannot overwrite existing history; new installs never write raw QR text. Device transfer keeps the document library but excludes QR history.
6. Take before/after light/dark screenshots on 320/360/600 dp, portrait/landscape, 200% fonts, RTL and TalkBack. Confirm no overlapping camera controls and no changing live indicators repeatedly announced.
7. Record actual image quality/latency metrics and compare the same physical paper on all three apps. Have native speakers review any new translations before release.

## Screenshot provenance and localisation review

Screenshots supplied by the owner depict competitor/reference interfaces rather than ScanPro itself. Treat them as design-research examples; do not count them as ScanPro screenshots or as evidence that a specific editor exists or functions in this repository. The proposed changes keep the codebase's existing privacy, sharing, permissions, six supported languages and animations, and add checked translations for the new scanner/tool UI. Competitor devices, motion timings, screenshots of the patched APK and native-speaker visual QA have **not** been executed in this environment.
