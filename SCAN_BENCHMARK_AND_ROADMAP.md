# Scan quality benchmark, competitor notes and roadmap

Status: 2026-11-11. Everything here is labelled **Verified** (measured or seen on the OnePlus CPH2569) or **Not verified**
(an assumption or something that could not be tested). Nothing is claimed about how another app works inside.

## 1. Competitors on the phone

Installed: Adobe Scan, OKEN Scanner (package `com.cambyte.okenscan`), iLovePDF, PDF Scanner (`pdfscanner.scan.pdf.scanner.free`).

### Verified
| | Cold start (ms, 4 runs, force-stopped between) | First run, without accepting anything |
|---|---|---|
| Adobe Scan | 990, 983, 1202, 1064 | A consent screen: scans are "processed in and saved to Adobe cloud storage"; OK is required to continue |
| OKEN Scanner | 934, 902, 1032, 1182 | Empty library, "Start Scanning", optional sign-in prompt |
| iLovePDF | 991, 1133, 610, 585 | Opens on a file browser listing device files, not a camera |
| PDF Scanner | 809, 919, 846, 834 | Language picker first |
| ScanPro (debug build) | 2824 on the first run; about 2000 to 2200 in earlier runs | Language picker, welcome, permission explainer, empty Home with Start scanning |

Caveats: the competitors are release builds and ScanPro is a debug build (no code shrinking, no baseline profile), so the start-up
comparison is **not like for like**. A fair run needs a locally signed, minified ScanPro build; that build type does not exist yet.
OKEN Scanner's launcher activity is in the `com.intsig.camscanner` namespace; I make no claim beyond that name.

### Not verified (not tested)
Everything else in the brief: their camera behaviour, auto-capture, edge accuracy, filters, OCR, PDF size, and animations. A fair
comparison needs printed test pages on a fixed rig, and Adobe Scan cannot be used without accepting cloud processing, which I did not
do on the owner's behalf. iLovePDF's home screen lists files from the phone, so I did not screenshot or open it.

## 2. ScanPro today

### Verified strengths
- Capture to save works end to end: capture, quality warning ("may need a retake"), review with crop, six filters, page strip, rename and
  folder, save, then a sheet with share, copy text, add pages, Drive, device save, print, scan another.
- A real computer-vision pipeline already exists: edge detection from five sources, background flattening, CLAHE, adaptive threshold,
  glare inpainting. Offline throughout.
- Dark and light themes, six languages (Indic text machine-written, needs review).
- Edge detection on generated scenes: 9 of 12 near perfect (about 0.2% corner error).

### Verified problems (and what was done)
| Finding | Status |
|---|---|
| No test measured detection accuracy | Fixed: `ScanDetectionBenchmarkTest` (F126) |
| Faint-edge pages (grey on grey, white on white) failed: 24% and 6% corner error | Fixed in F126: 1.5% and 1.6% |
| Camera screen redrew in full on every analysis frame | Fixed in F125 |
| Start-up did eager disk reads on the main thread | Reduced in F124 |
| Three zoom buttons sat over the page being framed | Fixed in F125 |
| A hard shadow across the page breaks detection (10.8% error) | **Open** |
| Control panel covers the lower preview, more with each page | **Open** |
| Two Save buttons on the review screen | Open, documented as deliberate |

### Not verified
Real printed documents, books with curved pages, handwriting, receipts that are really curled, OCR accuracy, PDF size against
competitors, battery and memory, mid-range devices, a release build.

## 3. Benchmark: how to run and read it
See `BUILD_AND_TEST.md`. Scenes: clean A4, strong perspective, 20 degree rotation, page near the frame edge, receipt on a dark desk,
low contrast, white on white, busy patterned mat, hard shadow, noise and softness, book spread, dim light. Metrics: found, mean
corner error as a percent of the frame diagonal, IoU against the true page, detection time.

| Scene | Before F126 (error % / IoU) | After F126 |
|---|---|---|
| clean, perspective, rotated, near edge, receipt, mat, book, dim, noisy | 0.16 to 0.23 / 0.97 to 0.99 | unchanged |
| low_contrast_grey_desk | 24.29 / 0.328 | 1.48 / 0.907 |
| white_page_white_table | 6.13 / 0.642 | 1.59 / 0.915 |
| hard_shadow_across_page | 10.77 / 0.354 | 10.77 / 0.354 (open) |
| Mean IoU | 0.849 | 0.920 |

Limits: synthetic scenes are flat colours with no lens blur, rolling shutter or real paper texture. They prove the geometry and
lighting cases; they do not replace real photographs.

## 4. Roadmap (by user impact, then effort, then risk)

1. **Real-photo corpus and a fair competitor run** (high impact, medium effort, low risk). Print the test pages once (A4 text, small
   text, receipt, handwriting, a book) and photograph them on a fixed rig; run each app on the same photos by import where the app lets
   you without an account. Without this, claims about being better than the competitors are unsupported.
2. **Hard-shadow detection** (high impact, medium effort, medium risk). The page edge disappears where a shadow is as dark as the
   surface. Options to measure with the benchmark: combine partial outlines into one quad, or fit lines to the visible edge segments.
3. **Enhancement and readability benchmark** (high impact, medium effort, low risk). Score each filter on the generated pages: text
   contrast, halo around letters, background evenness, colour accuracy, and OCR word accuracy. Then tune, instead of judging by eye.
4. **A release-like build type for honest timing** (medium impact, small effort, low risk): minified, debug-signed, with the baseline
   profile; then compare start-up and camera-open time against the competitors on equal terms.
5. **Control panel over the preview** (medium impact, medium effort, medium risk): move the preview upward or shrink the page strip,
   and change the outline mapping with it; check with the benchmark and by eye.
6. **Page-level performance budget**: capture-to-review time and save time per page, measured on this phone and a mid-range device.

## 5. Quality strategy
- Technologies in use: CameraX, OpenCV, ML Kit text recognition, all on device. No change proposed without a measured reason.
- Objectives, to be held with the benchmark: found on 12 of 12 scenes; corner error below 2% on every scene except known failures;
  detection under 60 ms per frame on this phone in a release build; no scene gets worse between commits.
- Never trade readable text for a stronger filter: the readability benchmark (item 3) must pass before a filter change ships.

## 6. Adobe Scan and iLovePDF, used end to end (2026-11-11)
Both were used on the phone with no sign-in. For Adobe Scan the owner asked for a full check, so I accepted its first-run consent
(scans processed and saved in Adobe cloud storage), chose "Only this time" for the camera, declined notifications, and deleted my one
test scan before it could upload (it showed "Waiting to upload…" because the phone was in airplane mode). The iLovePDF test scan was
deleted too. Nothing personal was opened. Everything below is **Verified** on the OnePlus unless marked.

### Adobe Scan
- Camera: modes Whiteboard, Book, Document, ID card, Business card; a star marks Whiteboard, Book and ID card as paid "Plus" modes.
  A centred coach tip ("Tap the screen when you're ready to scan. We'll find the borders, and take the photo for you") covers the
  preview until tapped. Controls: import, auto-capture toggle, shutter, flash, last-page thumbnail; a Scan / AI mode switch.
- Review: a name that is edited in place ("Adobe Scan 11 Nov 2026"), a bottom toolbar (Ask AI, Digitize, Retake, Crop, Rotate, Edit
  text), then Keep scanning, Share as link, and Save PDF with a chevron. The automatic enhancement blew the whites out on the test scene.
- Crop: handles at the corners and at the middle of each edge, Auto-detect, Straighten.
- Save options: Compress PDF is behind a 7-day paid trial (High, Low or No compression).
- After saving: an upgrade banner, "Waiting to upload…" (cloud by default), Share, Save as Word doc, Digitize, More, and a promotion
  for another Adobe product. More: Copy to device or Drive, Export PDF, Combine, Set password, Compress, Edit text, Fill & Sign,
  Save pages as JPEG, Modify scan, Rename, Move, Print, Delete.

### iLovePDF
- Four tabs: Home (lists files on the device), Files, Tools, Scanner, plus Settings.
- Tools (3-column grid, category chips): Merge, Split, Compress, PDF to Word, PowerPoint and Excel, Word, PowerPoint and Excel to PDF,
  Edit, Image to PDF, PDF to JPG, Sign, Watermark, Rotate, Unlock, Protect, Organize, OCR (marked Premium), Create PDF, Repair PDF, Page
  Numbers, Extract TXT, Compress image, Convert to and from JPG, Resize image.
- Scanner: two stages, "Scanned Images" then "Converted PDFs". The camera is very plain: one status line below the preview ("Looking
  for document…"), flash, an Auto-scan toggle, the shutter, nothing drawn over the viewfinder. Crop offers "Full border" and "Detect
  border" over a thirds grid. Edit offers five live-preview filters (None, Photo, Document, Black and White, Monochrome) and
  Border detection, Rotate and a perspective "Distortion" toggle. Finishing asks for a name and "Save as PDF". The result menu:
  Extract TXT, Convert to PDF, Rename, Share, Delete.

### What ScanPro took from them (F127, F128)
| Seen in | ScanPro before | Done |
|---|---|---|
| iLovePDF has Repair PDF | no such tool | **Repair PDF** (F127): re-reads a damaged file with the forgiving parser and rewrites it; if that fails, rebuilds the readable pages as pictures and says the text is no longer selectable; refuses a protected file and says to unlock it first. Unit tests: a PDF with its end cut off comes back with all 3 pages; garbage gives no output; a protected file is refused |
| Adobe "Save as Word doc" one tap after saving; iLovePDF PDF to Word | Word only inside Tools | **Share as Word** in the after-scan sheet (F128): writes the recognised text to a .docx; disabled with an explanation until text is found |
| Adobe compress and iLovePDF OCR are paid | free in ScanPro | nothing to do; a strength to keep |
| Adobe cloud upload by default | offline only | nothing to do; a strength to keep |

### Not taken, and why
- PDF to PowerPoint and PDF to Excel: layout conversion that would look worse than the PDF; no honest way to test it here.
- Share as link, Ask AI, Digitize: need Adobe's cloud; ScanPro is offline by design.
- Edge-midpoint crop handles and a "Straighten" control: not checked against ScanPro's crop editor yet; candidates for the next round.

## 7. iLovePDF tool parity (2026-11-11)
| iLovePDF tool | ScanPro now |
|---|---|
| Merge, Split, Compress, Organize, Rotate, Page Numbers, Watermark, Sign, Protect, Unlock, OCR, Image to PDF, PDF to JPG, Extract TXT, Compress / Convert / Resize image | already present |
| Repair PDF | added (F127) |
| PDF to Word | already present (PDF to Word or text) |
| PDF to Excel | added (F129): tables rebuilt from word positions, one sheet per page |
| Word to PDF, Excel to PDF | already present (Office to PDF) |
| PowerPoint to PDF | added (F130): one page per slide, text and pictures |
| PDF to PowerPoint | added (F131): one slide per page, as a picture |
| Edit PDF (add text, shapes, images to any PDF) | **missing**; ScanPro edits only its own scans |
| Create PDF | not separately; Images to PDF and Office to PDF cover it |
