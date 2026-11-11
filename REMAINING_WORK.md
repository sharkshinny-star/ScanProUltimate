# Remaining work

Everything still open on `claude/awesome-clarke-ussjo3`, as of 2026-11-08 (after F85; CI run 89 passed on the merge of the two review branches, run 90 covers R9, F82-F84 and F85). Generated from the "Open" table in
`AUDIT_FINDINGS.md`, which holds the full problem description and evidence for each ID; keep that table as the source
of truth and update this list when items close.

## Pending (added 2026-11-11, after CI run 131 went green on `dfe132f`)

Nothing below has been tried on a device unless it says so. CI covers compile, unit tests and lint only.

**Needs the owner**
1. **Undo history across sessions (item 7).** Design written, nothing built: `UNDO_PERSISTENCE_DESIGN.md`. Waiting on three
   answers: keep up to 20 earlier text states (including text from before a redaction) in plain app-private storage? Limits
   (20 steps, about 6 MB per document) or shorter? Keep a Recycle-bin document's history until it is purged?
2. **A native speaker's read of every new string** (written by the assistant, unreviewed): `strings_qr.xml`,
   `strings_sign_pdf.xml`, `strings_pdf_text_insert.xml` and the `lib_restore_aside_*` strings in `strings_library.xml`,
   in Hindi, Kannada, Malayalam, Tamil and Telugu.
3. **Delete the old branch** `ccr-d3174e53-3zuive` on GitHub (PR #10 is closed; its commits are all in the working branch).
4. **Dependabot pull requests** #15 (Gradle 9.8.2 to 9.9.0) and #16 (ZXing core 3.5.3 to 3.5.4) are open and unreviewed.
   Each costs a CI run. Dependabot is set to monthly, at most 3 Gradle pull requests, actions grouped into one.
5. **`main`** is about 25 commits behind the working branch. The owner chose to leave it for now.

**Device checks (R44)**
6. QR scanner (F134): two or more codes in one frame show tappable outlines and open nothing until tapped; one code still
   opens at once; a picture with several codes opens the chooser; the outlines line up with the codes on a real preview.
7. Scan history (F134): entries appear, reopen in the result sheet, favourite, delete, Clear all; the switch stops recording;
   a Wi-Fi code shows only the network name and search never finds its password.
8. QR generator (F134): each type (text, link, Wi-Fi, phone, email, contact, UPI) scans back correctly with another phone's
   scanner; Share as PNG and Save as document work.
9. Sign PDF (F135): draw and saved signatures, a typed date, several stamps on several pages, **a rotated PDF and a PDF with
   a shifted crop box** (the maths is only tested against existing code), a protected PDF (copy is unprotected, the screen says so).
10. PDF to text or Word (F136): a text PDF, a scanned PDF (OCR pages named in the result), a protected PDF.
11. Insert pages (F136): from a second PDF, a range in the order given, after a chosen page, with a protected second PDF.
12. Restore a moved-aside library (item 5, `MovedAsideLibraryTest`): the Library-screen dialog (Restore, Not now, Do not ask
    again) has no UI test. Downgrade then upgrade, and check documents come back without duplicates.

**Not started (from the owner's list)**
13. Keep OCR word boxes in backups (item 4): after a restore, Excel export falls back to plain lines.
14. Offer OCR on documents brought back by "Recover missing documents" (item 6): recovered pages have no text.
15. QR ideas: product barcodes and ISBN with "open in my browser" (no online lookup, offline-first); live text mode
    (Lens-style highlight, copy, call, translate).
16. Tool ideas: Redact PDF (text must really be removed, not covered), table to Excel, receipt and expense summary, batch tools.

**Known gaps and limits**
17. No test that `splitBookSpread` removes its halves when the save fails (it needs a seam to make the save fail).
18. `CacheCleaner` deletes `sign_*` temp files after one hour, so a signing session left open longer could lose its working copy.
19. Scan history stores raw text, so Wi-Fi passwords sit in `filesDir/scan_history.json`, unencrypted like the rest of the library (R20).
20. Multi-code outlines assume the preview and analysis share one 4:3 field of view with FIT_CENTER (`cropRect` is not used).
21. `upload-artifact` v7 (Dependabot bump) has not run: those steps only run on a failed build or a manual run.
22. CI cost: a push run with no saved Gradle cache took about 39 minutes (run 131); runs with the cache took 19 to 24. The
    account shares 2,000 Actions minutes a month. Push in batches (`AGENTS.md`).

## Before release: device checklist

The device suite (`connectedDebugAndroidTest`, 6 passed), Home search pill stability (R42), refactor smoke test (F81), rotation pass (R38) and document tab labels in Hindi and Telugu have run and passed (Tamil wraps a tab mid-word: R46) on a physical OnePlus CPH2569 (Android 15). The checks below remain open for device verification:

1. Launch animation (R24): cold start from the launcher on Android 12+ and on Android 11; with app lock on (no animation);
   rotate during it; share a file into the app (no animation). Also the F66 fixes: no dark start, no black frame after a
   language change.
2. Page-quality check (R25): blurred, glary and dark pages flagged in review with Retake; glossy covers, white-on-white
   pages and IDs with holograms not over-flagged; glare hint in the camera never blocks auto-capture.
3. "Scan saved" sheet (R36): a receipt (total + Copy total), a business card (Add to contacts), a letter (no smart action);
   the Searchable PDF badge and Copy text appear once text recognition finishes.
4. Camera work since run 86, all device-only: fast save and review-when-needed (F68), ID card both sides on one page
   (F67), camera strip with long-press delete and Undo (F74), duplicate-aware auto-capture (F75), Book mode two-page
   spreads and the spine guide (F80), the Scan tile and home-screen widget (F78).
5. Colour themes, Home tips and What's new (R19) in light and dark; tablet side rail Scan button (F52).
6. Android 17 / targetSdk 37 on a phone and a tablet or foldable (R2).
7. Large-font and landscape pass (F83, R39): Welcome and Lock on a tablet or at the largest font, the crop editor's two
   button rows, the Scan saved sheet with the keyboard open, the Tools progress card with Cancel and Back.
8. Safety and access pass (F82, F84, R40, R41): create a backup and a protected PDF (typed twice, show/hide), Share on a
   Wi-Fi code (no password), a calendar code (start, end), TalkBack on the sliders, 48 dp targets, a failed translation.
9. Camera after a rotation (F86): open the camera, turn the phone, take a page in each orientation; no page may be saved
   sideways and the outline must still match the page.
10. Report an issue (F86): Settings > Report an issue opens the mail draft without a pause, the attached
    `scanpro_issue_report.txt` has no folder paths or file names, and the draft says what it contains.
11. Compress to a target size (F86): choose a target well below the file's size and check the result is at or under it,
    or as close as the file allows.
12. Native-speaker check of the Hindi, Telugu, Kannada, Tamil and Malayalam strings: a model's pass over the 105 drafts since F64
    is done (F87, `TRANSLATION_REVIEW.md`, objective fixes applied); a speaker should read that file's "for a native speaker"
    list and the strings it names. Marathi, Gujarati, Bengali and Punjabi are deliberately not added (owner decision).

18. Layout fixes from the screenshots (F89): at font scale 2 the Settings pickers are radio rows and no word breaks; in
    landscape at font scale 2 all four rail tabs fit uniformly without jumping; the landscape camera panel controls stack
    symmetrically down the centre axis and show whole; the landscape Adjust page filter thumbnails are readable; in Tamil
    the bottom bar, rail and document tabs fit on one line; long status subtitles marquee smoothly; landscape dialog heights
    remain comfortably within screen boundaries (OrderFilesDialog 240 dp, merge dialog 220 dp).
19. Images to PDF and Recents (F90, F92): pick three photos, rotate one, crop one, add more images via toolbar, rotate all pages, revert edits, verify rotation preserves crops/order, verify staging cache is purged on dismiss/cancel/completion; open a PDF from a cloud or Downloads provider, close the app completely, reopen it from Recent and confirm the pages show; delete the file and tap its row to see the message.
20. Camera switching performance (F91): verify fast and smooth switching between Camera and Files with 0 frame drops > 250 ms.

## Owner decisions (resolved 2026-11-08)

- **Screenshots in debug builds**: Kept as-is. `MainActivity` skips `FLAG_SECURE` when `BuildConfig.DEBUG` to facilitate screenshot testing and screen recording during development/QA while locked. Release builds strictly enforce `FLAG_SECURE`.
- **R18 (Recycle bin actions on small phones)**: Closed as F85. "Empty" moved from the button row under the notice into the TopAppBar ⋮ dropdown menu. "Restore all" now spans the full width under the notice, preventing button crowding and text truncation on 360 dp screens.
- **R20 (Documents and database encryption at rest)**: Deliberately not changed / skipped. Documents, OCR text and database rely on Android file-based encryption and sandbox security. Encrypting with Keystore/SQLCipher/Tink AEAD adds key-loss risk and runtime performance penalty; app lock provides sufficient protection.
- **R21 (Exported activity internal actions)**: Deliberately not changed / skipped as low risk. Internal actions accepted via external intents only trigger internal navigation and require device/app unlock.
- **R22 (Crash reports file names)**: Deliberately not changed / skipped as low risk. Crash reports are strictly local on-device and never leave the device unless explicitly shared by the user.
- **R23 (ML Kit telemetry & internet permission)**: Closed as F88. The owner submitted the Play Data safety form (2026-11-08) from the draft in `PLAY_STORE_CHECKLIST.md`. Still before release: fill the 8 highlighted fields in `docs/privacy-policy/index.html` (the finished policy page, written to match the form), host it at a public HTTPS URL, and set `scanpro.privacyPolicyUrl=<url>` in `gradle.properties` so the Settings link appears.
- **Translations mechanical check**: Verified complete. All 5 non-English languages (Hindi, Telugu, Kannada, Tamil, Malayalam) contain 100% of the 1,354 strings with exact matching placeholders to English; native speaker review will assess tone and nuance prior to store launch.
- **Old branches**: `claude/adoring-dijkstra-mvz8lc` and `claude/gracious-davinci-5lpczt` are fully merged into this branch but still exist on GitHub; delete them from GitHub's Branches page or run `git push origin --delete claude/adoring-dijkstra-mvz8lc claude/gracious-davinci-5lpczt` with your own login.
- R9 (splitting `DocumentRepository`) is done (F81); R39, R40 and R41 are done (F82-F84); R18 is done (F85); R38 and R42 are done (verified on physical device).

## PDF Tools backlog

Seven tool improvements (password prompts in every tool, Unlock's "Done" notice, file names in Split and PDF to
Images, page numbers/watermark, Sign PDF, PDF to Word, insert pages) and the device checks for F48–F50:
see `TOOLS_IMPROVEMENTS.md`.

## Open items by priority

| ID | Priority | Area | Where | Open issue | Next step |
|---|---|---|---|---|---|
| R2 | P1 | Compat | `app/build.gradle.kts` `targetSdk = 37` | Raised to 37 (Android 17) after a code review against the Android 17 behaviour changes (see F12). | Device-test on Android 17, including a large screen. |
| R7 | P1 | Testing | whole app | Nothing verified on a device. | Device pass; Compose UI tests in CI on an emulator; golden comparison for the screenshots. |
| R19 | P2 | UX | `core/designsystem/Palettes.kt`, `core/designsystem/PalettePicker.kt`, `feature/library/HomeTips.kt`, `ui/WhatsNewSheet.kt`, `res/values-*/strings_themes_tips.xml` | Colour themes, Home tips and the What's new sheet are verified only by JVM renders (`ColourThemeScreenshotTest`) and tests. | Device pass through each colour theme in light and dark; native-speaker review of `strings_themes_tips.xml`. |
| R24 | P2 | UX | `ui/LaunchIntro.kt`, `res/drawable/ic_launch_icon.xml`, `MainActivity.kt` | The new launch animation is verified by JVM renders compared frame by frame with the Figma render, `LaunchIntroTest` (timing, tap to skip) and `LaunchSmokeTest` (56 launches, Android 7-17), not on a device. | On a device: cold start from the launcher (Android 12+ and 11), app lock on, rotation during the animation, a share into the app (no animation). |
| R25 | P2 | UX | `scanner/PageQuality.kt` | The blur, glare and darkness limits come from the live detector and synthetic tests, not from real photos. | Run `SCAN_QUALITY_TEST_PLAN.md` photos through `OpenCvScanProcessor.assessQuality` and tune `GLARE_MIN_SHARE` / the clipped level. |
| R36 | P2 | Testing / UX | `feature/document/ScanSavedSheet.kt`, `scanner/PageQuality.kt`, `ui/LaunchIntro.kt` | Left over from the 2026-11-05 after-scan, page-quality and launch work; the suite and lint half is done (F80 run). | On a device scan a receipt, a business card and a letter and check the sheet; then R24/R25 device checks. |

Fixed items and their evidence are in the "Fixed" table of `AUDIT_FINDINGS.md`; build and test results in `BUILD_AND_TEST.md`.
