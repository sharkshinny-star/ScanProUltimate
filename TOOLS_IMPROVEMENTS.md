# PDF & Image Tools: improvements left to do

Last updated: 2026-11-10. F107 added standalone Crop Image tool, pre-crop capability in Image Tools dialogs, advanced image tools optimisation (dimensions vs percent, presets, direct PDF export), and new PDF Tools suite (Watermark, Page numbers, PDF to Text). F104 added Image Tools suite (compression to target KB/MB, 100% lossless JPG to PNG conversion, dimension resizing with aspect-ratio lock, passport/avatar presets). F103 resolved Unlock background notification and Split/Export naming. When an item is done, move it to `AUDIT_FINDINGS.md` (Fixed, with its F id and commit) and delete it here.
Last updated: 2026-11-10 (items 5 to 7 built as F125 and F126, CI green, not on a device: see R44). Collected after F48 to F50 (Images to PDF on white, Merge password prompt, first-launch
permissions). Nothing here is started. When an item is done, move it to `AUDIT_FINDINGS.md` (Fixed, with its F id and
commit) and delete it here.

Suggested order: items 1 to 3 together (one change, mostly reuse of the F49 Merge work), then item 4 as the first new tool.

## Fixes to existing tools

### 1. Every tool should ask for a protected PDF's password (P2)
- **Now:** only Merge asks (F49). Split, Extract, Rotate, Organise (`PdfToolsEngine.kt`), Fill form (`PdfFormEngine.kt`)
  and Make searchable (`PdfOcrEngine.kt`) open files with `PdfToolsEngine.loadPdf`, which stops a password-protected
  PDF with "This PDF is password-protected. Unlock it first with the Unlock PDF tool."
- **Do:** give these tools the Merge treatment: an `openPdf(file, password)` call, a "needs password" exception, a
  question in `PdfToolsViewModel` (memory-only password, cleared when the job ends) and the existing `PasswordDialog`.
  Also check Compress and PDF to Images (they render with `PdfRenderer`).
- **Note:** the output is unprotected, as with Merge; the dialog must say so (no silent weakening of security).

## New tools (reuse existing code)

### 4. Page numbers and watermark for any PDF (Fixed in F107)
Implemented in F107 (`page_numbers` and `watermark` in `PdfToolsEngine`, `PdfToolsViewModel`, and `PdfToolsScreen`):
stamps custom text watermark diagonally at 45° or horizontally at 0° with colour chips, opacity, and font size sliders;
and stamps page numbers in 5 positions ("Page 1 of N", "1 / N", "- 1 -", "1") with option to skip cover page.

### 5. Sign PDF (P2)
Reuse the signature pad and placement step from the document editor (F25) on pages rendered from any PDF; stamp the
signature image with PDFBox at the chosen spot.

### 6. PDF to Word or text (P3 - Text extraction fixed in F107)
Text recognition (`OcrEngine`, `PdfOcrEngine`) and the Word export (`DocxExporter`) exist for scans. In F107, added `pdf2text` tool extracting text layer to `.txt` files with word and page counts. PDF to Word (`.docx`) remains for full multi-format export.

### 7. Insert pages in Organise (P3)
Add a blank page or pages from another PDF at a chosen position in the Organise sheet (`organizePages` takes
`PageSpec`s; extend it with an inserted-page source).

## Open items in `AUDIT_FINDINGS.md` that touch the tools

- **R28:** no file-size estimate per quality setting when exporting (competitors show one).
- **R7:** PDF tools never checked on a device; `ScreenScreenshotTest` does not render the Tools screen or its dialogs.

## Device checks still owed for the latest tool work

- **F48:** a transparent PNG (logo, signature, screenshot) through Images to PDF comes out on white; a rotated phone
  photo is upright; Cancel works part-way.
- **F49:** Merge with one and with two password-protected PDFs; wrong password asks again; the merged file opens
  without a password; the background notice when you switch apps.
- **F50:** fresh install: launch animation, language, welcome, permission page; Allow, Not now and Back; Android 12
  (camera only) and Android 13+.
