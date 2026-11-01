# UX and UI review (static code reading)

Date: 2026-11-01, branch `claude/awesome-clarke-ussjo3`, at `e7b141e`.

**Method and limits.** Read from the source only. Nothing was run: this container had no Android SDK or device. "Confirmed" means
the code at the cited line does what is described. "Inferred" means the effect on screen depends on behaviour that was not
observed. Open items are tracked as R12 to R16 in `AUDIT_FINDINGS.md`.

## Status after the first fixes (F38, not compiled or run)

Also fixed in code (F39): R13 (localised fallback titles, Office-to-PDF notes and file sizes) and R15 (landscape layouts for the camera and the review).
Fixed in code (F38): R12 (editors), the review "Save page" and Delete page items, the Done and Auto/Manual pills, pinch zoom and the
zoom slider, the retake pill overlap, and background scans opening over other screens. Still open: the mode lock and filter
rules and filmstrip in R14, R16. Delete page asks for confirmation instead of offering undo.
Correction: the "Scan saved" notification is already skipped while the app is visible, so a message and a notification did not
arrive together as item 4 below says.

## A. Findings

### Editors lose work (R12, confirmed)

- `MarkupEditorDialog`, `CropEditorDialog`, `EraserDialog` and `SignaturePlacementDialog` are full-screen `Dialog`s. System Back and
  their Cancel button call `onDismiss` at once (for example `MarkupEditorDialog.kt:95,200`), with no "Discard changes?" step.
  Only `FormFillDialog` asks (`FormFillDialog.kt:72-74`).
- Mark-up state is plain `remember` (`MarkupEditorDialog.kt:78`, `EraserDialog.kt:80`), while the flag that opens the dialog is
  `rememberSaveable` (`DocumentScreen.kt:474`). After a rotation or theme change the dialog stays open but the mark-up is reset to
  its starting state, so the work disappears without any sign.
- Back handling is rare: `BackHandler` appears in only four places (Welcome, Library selection, scan review, camera).
  The PDF viewer has none. (An earlier note in this review wrongly listed the PDF viewer; corrected here.)
- Fix: confirm discard when there are edits; keep editor state across recreation.

### English in a localised app (R13, confirmed)

- Fallback titles "Imported …" and "Scan …" (`DocumentRepository.kt:192,1138`) show in the library and become the exported file name.
- Office to PDF notes "… cells are shown; the rest of the workbook was not converted." are written into the user's PDF
  (`OfficeToHtml.kt:633,873`).
- `formatBytes` hard-codes "B/KB/MB/GB" (`UiUtils.kt:140`).

### Scan flow (R14, confirmed unless marked)

1. **"Save page" does not save.** On every page but the last it moves to the next page (`ScanReviewScreen.kt:479`), and the label
   never changes. Done on the camera opens the review on the newest page (`CameraScannerScreen.kt:409-413`), so a user who goes back to
   page 1 to fix it and taps "Save page" lands on page 2.
2. **Delete page is immediate and permanent** (`ScanReviewScreen.kt:282-292`): no confirmation, no undo, next to Retake and the menu.
3. **Done is a bare number.** The control that finishes a scan is the page-count pill (`CameraScannerScreen.kt:1185-1198`); the word
   "Done" exists only as an accessibility label. Before the first capture the same slot shows "Auto", which reads the same whether
   auto-capture is on or off (colour is the only difference).
4. **After Save**, the camera closes to Home and the document opens by itself when the import finishes (`ImportScanWorker.kt:102`,
   `ScanProApp.kt:152`), wherever the user has gone meanwhile, with a message and a notification at the same time (inferred: how
   this feels depends on import time). There is no "saving" card on Home.
5. **Modes** are locked once a page exists (`modesEnabled`, `:1098`) with no explanation, and none says what it does. Text mode
   copies the text instead (`ImportScanWorker.kt:100`), which is only learnt after capture.
6. **Filter rules clash.** Choosing a filter changes every page not set by hand (`ScanReviewScreen.kt:423`), while the menu also
   offers "Use … on all".
7. **Review layout is dense** (inferred): top bar, stage, stepper, filter strip, three sliders and three buttons leave a small stage
   on a short phone. Pages can only be reordered through menu items. Brightness/contrast/saturation have no reset and are not
   saved across process death.
8. **Retake overlap** (inferred from the code): the guidance pill and the "Retaking page n" pill are both drawn top-centre at 62 dp and
   64 dp when retaking in a normal mode (`CameraScannerScreen.kt:888,939`).
9. Capture uses `CAPTURE_MODE_MINIMIZE_LATENCY` (`:603`); `MAXIMIZE_QUALITY` is worth comparing in dim light.

### Camera controls (R15)

- No pinch-to-zoom (confirmed: the preview touch listener only focuses, `:805-822`). Zoom is 1x/2x/5x chips about 34 dp across.
- The menu item "Zoom & exposure" opens only an exposure slider (`:1012,1067-1082`).
- No landscape handling (confirmed: no orientation attribute in `AndroidManifest.xml`, none in the screen). Look unknown; needs a device.

### Touch targets, screen readers, dialogs (R16)

- Sizes forced below 48 dp: page-move arrows 26 dp (`DocumentScreen.kt:1486`), review page stepper 36 dp (`ScanReviewScreen.kt:406,410`),
  zoom chips 34 dp. Whether Material still gives a 48 dp touch area with an outer `Modifier.size` was not checked (inferred).
- 78 uses of `contentDescription = null`; most sit inside labelled controls. Check with TalkBack: `LibraryScreen.kt:791`,
  `KeptScansScreen.kt:239`, `LibraryScreen.kt:1097`.
- About 25 hard-coded colours outside the design system. Several are intentional dark-camera colours; `FavouriteGold`, the pen
  colours and the `FilterStrip` blue do not follow the theme and may have low contrast in dark mode.
- Dialogs without a scroll container: `Components.kt` (3), `SignaturePad.kt` (2), `BookSplitDialog.kt`. Buttons may fall off-screen
  at a large font size or in landscape.

## B. Sharing and export (checked, no defects found)

- `shareFile` (`core/ui/UiUtils.kt:198`) shares through `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION` and a chooser; it handles
  one file (`ACTION_SEND`) and many (`ACTION_SEND_MULTIPLE`), and tells the user if a file is missing.
- The provider is not exported and `file_paths.xml` exposes only `documents/`, `exports/` (cache), `crash-reports/` and
  `pro_scanner_session/`. That matches the comment in the file.
- There is no collaboration feature (no accounts, live editing or comments), by the offline-first rule. Sharing is by system chooser,
  "Save to Drive" and encrypted `.scanpro` backups.
- Not checked: whether Share from a locked folder asks for unlock first, and cleanup of the cache `exports/` folder.

## C. Proposed structure for scanning

- **Camera:** top bar Close, torch/flash, an Auto/Manual toggle that names its state, and ⋮. Bottom: mode strip with a one-line hint
  for the chosen mode, shutter, a last-page thumbnail with a count badge, and a pill reading "Done ✓ n". Add pinch-to-zoom.
- **Review:** a thumbnail filmstrip (drag to reorder, "+" tile to add, delete with Undo). Bottom bar: Crop, Rotate, Filters,
  Adjust (a sheet with Reset), Retake. One primary button, "Save (n pages)", that always saves everything. An explicit
  "Apply to all pages" switch replaces silent filter propagation.
- **After save:** a "Saving…" card on Home that opens the document when tapped. Open it automatically only if the user is still on
  the post-save screen.

## D. Checks to run on a device

1. Five pages, back to page 1, tap "Save page": does it save or advance?
2. Delete a page: can it be recovered?
3. Edit mark-up, rotate the phone: is the work still there?
4. Pinch to zoom; rotate the phone in the camera; retake a page and look for overlapping pills.
5. Save a scan, then start a search: does the document open over it?
6. Hindi/Tamil/Telugu: look at fallback titles and file sizes.
7. TalkBack on the three controls above; 200% font on the dialogs without scrolling.
