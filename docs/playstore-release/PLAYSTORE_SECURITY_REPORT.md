# ScanPro: security report for the Play release

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: source review (security audit S1–S23 of 2026-11-18 at `e8a31d3`, re-checked against HEAD for the fixed items),
CI results and official documentation. Not devices, not a penetration test. "Pending CI" means the fix is written but has
not yet been compiled or tested.

Related: [Fixes](PLAYSTORE_FIXES.md) · [Privacy](PLAYSTORE_PRIVACY_AUDIT.md) · [Permissions](PLAYSTORE_PERMISSIONS_AUDIT.md) ·
[Test report](PLAYSTORE_TEST_REPORT.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

Paths are under `app/src/main/java/com/example/scanpro/` unless they start with `app/`.

## 1. Summary

| Area | Finding | Severity (audit) | Status at HEAD |
|---|---|---|---|
| Redact text / Redact areas | S1 original pages reachable through annotations, links, widgets, shared resources | P0 | Fixed in code (`fbfe713`, `1de4db4`); tests pending CI |
| | S2 terms only in form fields, comments, bookmarks, metadata | P1 | Search covers comments and form fields; outlines, `/Info`, XMP, attachments not copied (catalogue reduced to `/Type`, `/Pages`); pending CI |
| | S3 boxes misplaced on rotated pages, case folding, ligatures | P1 | Fixed in code (per-glyph boxes with page rotation, per-glyph folding); pending CI; real files NOT VERIFIED |
| | S4 tests could not catch leaks | P1 | `EditToolsTest` (12) reads every object back; pending CI |
| | S7 unredacted inputs left in cache after a kill | P2 | Fixed (`9a3efe5`) |
| | S8 inherited resources lost on kept pages | P2 | Fixed in code; test `aPageWithInheritedResourcesKeepsItsFontWhenCopied` pending CI |
| Document editor redaction | S5 originals kept inside the app and moved by device transfer | P2 | Open (by design; disclosed in backups text) |
| Edit PDF text | S6 text in form XObjects covered with a white box; `/ActualText` | P2 | Open |
| Sign PDF | S9 visual stamp only; breaks an existing digital signature silently | P2 | Open; wording is accurate ("Add your signature", no "digital/legal" claims) |
| Protect / Unlock | S10 silent AES-128 fallback; owner password = user password | P2 | Open |
| Flatten | S11 "so nobody can change them" | P2 | Fixed in English (`1de4db4`, `3692fbf`); five translations of two strings still old |
| Watermark | S12 removable, extractable text | P2 | Accepted (no permanence claim) |
| QR history | S13 encrypted with Keystore AES-GCM | OK | — |
| Locked folders | S14 hide, do not encrypt; other surfaces unchecked | P2 | Device check |
| App lock | S15 UI gate (no `CryptoObject`); `FLAG_SECURE` only when lock is on, never in debug | P2 | Accepted; describe as privacy lock |
| Backups | S16 cloud backup off; device transfer on; restore zip-slip safe | OK/P2 | — |
| Temp files, FileProvider | S17 `documents/` root also covers original photos | P2 | Open (hardening) |
| Intents | S18 custom actions from any app | P2 | Open |
| ZIP / XML | S19 caps, path stripping, XXE off | OK | — |
| Malformed PDFs | S20 flate bombs; PDFium in process | P2 | Open |
| UPI | S21 label prefers the code's own `pn` | P2 | Open |
| Crash reports | S22 exception messages not redacted | P2 | Open |
| Disclosure | S23 "nothing uploaded" vs ML Kit diagnostics | P1 | Open (PS-07) |

## 2. Redaction (the release's highest security risk)

What the code now does (`imaging/PdfCropRedactTools.kt`):
- Pages with matches are rasterised with black boxes; pages without matches are copied by `importClean`
  (l.714): resources limited to the XObjects the page draws (`cleanResources`), inherited resources set on the page,
  `/B`, `/PieceInfo`, `/Metadata`, `/Thumb` removed, widget `/Parent` cut.
- `scrubReferences` (l.789) removes references back into the source document; the output catalogue keeps only `/Type`
  and `/Pages` (l.690-691), so no outlines, names, attachments, AcroForm, JavaScript or open action; `/Info` is removed.
- `verifyRedacted` (l.830) reopens the saved file, walks every object reachable from the trailer, and fails when a
  `/Type /Page` dictionary is not one of the document's pages, or when a string or decoded non-image, non-font stream
  contains a term. The caller deletes the output and shows `tools_redact_err_failed`.
- The redacted copy is never named after a redacted word (`68bb6cc`).

Residual risks:
- The read-back check only covers ASCII terms of three or more characters; a term in Devanagari or another script, or in
  a font's custom encoding, relies on rasterising and the page-count check.
- Box placement on real-world rotated and scanned PDFs is unit-tested only (Robolectric/JVM), not on files from users.
- UI strings still say "for good" and "cannot be recovered" (`strings_tools_new.xml:132, 134, 185`). Defensible once the
  fail-closed check passes CI; keep under review.
- Whether `PdfRenderer` draws annotations in print mode (affects pages with matches that also hold form values): NOT VERIFIED.

## 3. Signing, protecting, flattening, watermark

- **Sign PDF** (`imaging/SignPdfEngine.kt`): draws a PNG on the page and saves; no `PDSignature`. It is a picture of a
  signature. The listing must say "visual signature, not a certified digital signature" ([Store listing](PLAYSTORE_STORE_LISTING.md)).
  Saved signature PNGs live in `filesDir/saved_signatures` and can be lifted from the output with `pdfimages`.
- **Protect** (`imaging/PdfSecurity.kt:24`): AES-256 (rev 6), falling back to AES-128 silently; minimum length 8.
- **Unlock**: needs the password; writes an unprotected copy.
- **Flatten** (`imaging/PdfCleanupTools.kt`): field values become page text (still extractable); annotations without an
  appearance are kept.
- **Watermark** (`imaging/PdfToolsEngine.kt`): separate content stream and real text; removable.

## 4. Storage, logging, temporary files

- All user data is in app-private storage; nothing in `getExternalFilesDir` (audit grep). See [Privacy §2](PLAYSTORE_PRIVACY_AUDIT.md).
- Logging: release strips `Log.v/d/i` (`app/proguard-rules.pro:49-53`, `123b048`); `w`/`e` remain. No OCR text, QR
  payloads or passwords are logged (grep). Some warnings include internal file names (`feature/document/CropEditorDialog.kt`, `imaging/OcrEngine.kt`).
- Temporary files: inputs deleted in `finally`; cache swept on start for the tool prefixes (`work/CacheCleaner.kt:26`);
  decrypted working copies removed by the tools view model; share copies in `cacheDir/exports` kept 24 h, unprotected.
- FileProvider (`res/xml/file_paths.xml`): `documents/`, `cache exports/`, `tool_results/`, `crash-reports/`,
  `pro_scanner_session/`; not exported; read grants only for URIs the app creates.

## 5. Intents and input

- `MainActivity` accepts VIEW/SEND only for `content://` URIs from other authorities (no `file://`, not its own provider);
  `taskAffinity=""` against task hijacking.
- QR results: web links only http(s), shown in a preview before opening; `intent:` and `javascript:` never launched;
  UPI hands the `upi://` URI to a chooser only on tap. No money is handled by ScanPro.
- ZIP and Office input: size caps, entry names stripped, XML external entities off. Backup restore: zip-slip check,
  chunked AEAD.
- PDFs: PDFBox with temp-file memory but no input size cap; platform `PdfRenderer` (PDFium) runs in the app process, so
  its safety follows the phone's patch level.

## 6. Dependencies and CVEs (as audited, not re-searched)

| Library | Version | Known CVE affecting the app |
|---|---|---|
| PdfBox-Android | 2.0.27.0 | None found for core 2.0.27 ([Apache PDFBox security](https://security.apache.org/projects/pdfbox/)); 2026 CVEs affect the Examples module only. Port's backporting of later hardening NOT VERIFIED |
| ZXing core | 3.5.4 | None found (search only) |
| OpenCV | 4.14.0 (built from source) | NOT VERIFIED |
| CameraX | 1.6.2 | NOT VERIFIED |
| ML Kit / Play services | various | Closed source; updated through Play services. NOT VERIFIED |
| androidx.biometric | 1.1.0 | NOT VERIFIED |

Dependabot (`.github/dependabot.yml`) checks Gradle and Actions monthly.

## 7. Release signing and secrets

- No keystore, password or `local.properties` is committed (`git log --all` search in the platform audit; `.gitignore`
  covers `*.jks`, `*.keystore`, `keystore.properties`, `signing.properties`, `local.properties`).
- Release signing comes from `SCANPRO_KEYSTORE*` properties or environment variables; without them the build is
  **unsigned and still succeeds** (PS-03). Play App Signing is not set up (no Console access).

## 8. Residual risks to accept or fix before release

1. Redaction fixes unproven until CI passes `EditToolsTest`; non-ASCII terms rely on rasterising (P1).
2. "Nothing uploaded" wording (P1, PS-07).
3. Sign PDF silently invalidates existing digital signatures (P2, S9).
4. Silent AES-128 fallback in Protect (P2, S10).
5. UPI label shows the code's self-declared name first (P2, S21).
6. Crash-report exception messages may contain a file title (P2, S22).
7. Custom actions on the exported activity (P2, S18).
