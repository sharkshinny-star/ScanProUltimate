# ScanPro: Google Play store listing (draft)

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: features checked against the source (string resources, tool list, manifest), listing rules from official Google
documentation. Not devices: no screenshot has been taken and no claim has been checked on a phone. The owner approves
the final text.

Related: [Policy changelog §12](PLAYSTORE_POLICY_CHANGELOG.md) · [Permissions §3](PLAYSTORE_PERMISSIONS_AUDIT.md) ·
[Declarations](PLAYSTORE_DECLARATIONS.md) · [Manual actions](PLAYSTORE_MANUAL_ACTIONS.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

Limits ([Store listing](https://support.google.com/googleplay/android-developer/answer/9859152),
[Metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842)): app name 30, short description
80, full description 4,000 characters; no emojis, no ranking or price claims ("#1", "best", "free"), no testimonials, no
keyword stuffing. Counts below were made with Python `len()` on the exact text (Unicode characters; "•" counts as one).

## 1. App name (28 / 30)

```
ScanPro: Scanner & PDF Tools
```

Alternative: `ScanPro` (7). The launcher label stays `ScanPro` (`app_name`).

## 2. Short description (71 / 80)

```
Scan documents to PDF, recognise text and work with PDFs on your phone.
```

## 3. Full description (2705 / 4,000)

Rules followed: no superlatives; OCR described as imperfect; Sign PDF described as a visual signature; UPI described as a
QR generator and hand-off (no payments); ML Kit diagnostics and downloads disclosed; the All files access feature is
not in the Play build and so is not described.

Owner decision 2026-11-19 (F225): the Play build has **no** All files access (`-Pscanpro.allFilesAccess=false`), so the
"FILES ON YOUR PHONE" section and the matching release-note line are removed below. If the declaration is ever filed and
approved, put this back after the scan-history line: `FILES ON YOUR PHONE / • With your permission, list and open the PDFs, images, Word, Excel and other documents already on your phone, and send them to the PDF tools. ScanPro only reads these files; everything else works without this permission`

```
ScanPro turns your phone into a document scanner and PDF toolkit. Your scans, recognised text and documents are kept in the app's private storage on your phone. There is no account and no advertising.

SCAN
• Camera scanning with automatic page-edge detection, perspective correction and crop you can adjust by hand
• Scan modes for documents, books (split a two-page spread) and ID cards (front and back on one page)
• Page filters (such as Original, Magic Colour and black and white), plus rotate, reorder and retake
• Import photos with Android's photo picker, or PDFs and images shared from other apps

RECOGNISE TEXT (OCR)
• Recognises Latin, Devanagari, Chinese, Japanese and Korean text on the phone using Google ML Kit
• Search your library by the words in your scans, then copy, correct or export the text
• Make an existing PDF searchable
• Text recognition is not perfect: check important details such as numbers and names before you rely on them

PDF TOOLS
• Merge, split, extract, insert, rotate and organise pages; remove blank pages
• Compress PDFs and images; resize, crop and convert images
• Convert images and Office files to PDF, and PDF to images, Word, Excel or text
• Add page numbers, headers and footers, watermarks and several pages per sheet
• Fill PDF forms, flatten forms and comments, crop pages, convert to greyscale, invert colours
• Edit the text of a PDF, and redact words or areas: redacted pages are redrawn as pictures and the saved copy is checked before it is kept
• Sign PDF: draw your signature and place it on a page as an image. This is a visual signature, not a certified digital signature
• Protect a PDF with a password, or remove a password you know

QR AND BARCODES
• Scan QR codes and barcodes, with a preview before a web link opens
• Create QR codes, including a UPI payment code for your own UPI ID. ScanPro does not make payments: paying opens the UPI app you choose
• Keep a scan history, stored encrypted on your phone, or turn it off

PRIVACY AND SECURITY
• Optional app lock with your biometrics or screen lock, and locked folders
• Password-protected backups you can export and restore
• Your documents and recognised text are not uploaded. Google ML Kit, which powers text recognition, barcode scanning and translation, sends Google diagnostic information (such as device model and performance data, not your images or text). Language models and the optional translation feature are downloaded through Google Play when first needed

ALSO
• Translate recognised text on the phone after a one-time language download
• Home-screen widget and Quick Settings tile to start a scan
• App available in English, Hindi, Tamil, Telugu, Kannada and Malayalam
```

Claims to confirm on a device before publishing: OCR scripts offered; widget sizes; the Quick Settings tile; that the
translation download and OCR language packs arrive through Play as described.

## 4. Category, tags, contact

| Field | Draft | Status |
|---|---|---|
| App or game | App | Owner confirms |
| Category | Productivity (alternative: Business) | Owner decision |
| Tags | Choose from Play's list: document scanner, PDF, productivity (only tags Play offers) | Owner |
| Contact e-mail (required) | One address shared with the privacy policy and the app (PS-08: they differ today) | **Owner decision** |
| Website | Optional; may be the hosted privacy policy's site | Owner |
| Privacy policy URL | Hosted `docs/privacy-policy/index.html` | **Not hosted** |

## 5. Graphic assets

| Asset | Spec ([Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)) | Status |
|---|---|---|
| App icon | 512 × 512 px, 32-bit PNG with alpha, ≤ 1024 KB | Made 2026-11-19: `assets/play-icon-512.png` (rendered from the launcher icon's vector paths and gradients, full square, 175 KB; the owner has not approved it yet) |
| Feature graphic | 1024 × 500 px, JPEG or 24-bit PNG, no alpha | Made 2026-11-19: `assets/play-feature-graphic-1024x500.png` (icon glyph, name, "Scan, sign and organise documents on your phone", brand gradient, RGB without alpha; the owner has not approved it yet) |
| Phone screenshots | 2–8, JPEG or 24-bit PNG, no alpha, shortest side 320–3840 px, long side ≤ 2 × short side | Not captured |
| 7" and 10" tablet screenshots | Same rules; needed for tablet visibility | Not captured |
| Promo video | Optional public or unlisted YouTube URL, ads off | Optional |

## 6. Screenshot capture plan (from the real app, release build, phone with clean demo data)

Use only documents you own or made for the purpose (no real ID cards, bank letters or UPI IDs). Light theme, English;
optionally repeat in Hindi. No device frames or text that claims rankings.

1. Camera scanning a page with the edge outline visible.
2. Crop and filters screen after capture.
3. Library with several documents and the search field showing a word found by OCR.
4. Recognised text view of a scanned page.
5. Tools screen with the PDF tools list.
6. A tool page with its result preview (e.g. Compress or Merge).
7. QR scanner result sheet (a URL with the preview) or the Create QR screen.
8. Files on this phone list (default build only) or Settings > Privacy.

Tablet: the same screens in landscape on a 10" tablet once large-screen layouts are checked (R2).
Robolectric screenshots (`ScreenScreenshotTest`, `-Pscanpro.screenshots=true`) are for review only, not for the store.

## 7. Release notes for 4.2.0 (260 characters; Play's per-language limit is 500: NOT VERIFIED in this session's sources)

```
• Redaction now checks each saved copy and refuses to keep it if a redacted word is still inside the file
• Rebuilt page, image, convert and edit tools, each with a preview of the result before it runs
• Resizable home-screen widget
• Fixes and clearer wording
```

Drop the third bullet for the build without All files access.

## 8. Translations of the listing

The app ships in English, Hindi, Tamil, Telugu, Kannada and Malayalam, but the in-app translations are unreviewed (PS-14).
Publish the listing in English first; add other languages only after a native speaker reviews them.
