# PDF & Image Tools: improvements left to do

Last updated: 2026-11-09. F104 added Image Tools suite (compression to target KB/MB, 100% lossless JPG to PNG conversion, dimension resizing with aspect-ratio lock, passport/avatar presets). F103 resolved Unlock background notification and Split/Export naming. When an item is done, move it to `AUDIT_FINDINGS.md` (Fixed, with its F id and commit) and delete it here.

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

### 4. Page numbers and watermark for any PDF (P2)
`PdfExporter` already numbers pages for scan exports. Add a tool that stamps "n / total" (position, size) and/or a
text watermark (text, opacity, angle) onto an existing PDF with PDFBox content streams.

### 5. Sign PDF (P2)
Reuse the signature pad and placement step from the document editor (F25) on pages rendered from any PDF; stamp the
signature image with PDFBox at the chosen spot.

### 6. PDF to Word or text (P3)
Text recognition (`OcrEngine`, `PdfOcrEngine`) and the Word export (`DocxExporter`) already exist for scans; chain them
for any PDF, using the PDF's own text layer when it has one.

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
