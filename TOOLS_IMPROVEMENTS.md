# PDF Tools: improvements left to do

Last updated: 2026-11-10 (items 5 to 7 built as F103 and F104, not yet compiled: see R44). Collected after F48 to F50 (Images to PDF on white, Merge password prompt, first-launch
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

### 2. Unlock's background notification says "Done" while it waits (P3)
- **Now:** `inspectForUnlock` and a wrong unlock password end the job with a `null` result, so `launchTool` posts the
  generic "Done" notice when the app is out of sight, although the app is waiting for a password.
- **Do:** set `endedWithQuestion` and post the question with `notifications.taskFailed(…)`, as `runMerge` does.

### 3. Split and PDF to Images name their files generically (P3)
- **Now:** files in the zip are `page_001.pdf`, `part_1_pages_1_to_5.pdf`, `range_1_pages_…pdf`, `page_001.jpg`
  (`PdfToolsEngine.splitPdf`, `pdfToImages`), so unpacked files don't say which PDF they came from.
- **Do:** prefix the source name (`sourceBaseName`), e.g. `Invoice page 001.pdf`; keep names file-safe and short.

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
