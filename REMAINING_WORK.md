# Remaining work

Everything still open on `claude/awesome-clarke-ussjo3`, as of 2026-11-19 (after F216 and F205): 32 open items (11 P1, 16 P2, 5 P3).
Rewritten by hand from the "Open" table in `AUDIT_FINDINGS.md`, which holds the full problem description and evidence for
each ID; that table is the source of truth. When an item closes there, remove it here, and change the date above.

Nearly every item is a check on a phone: cloud sessions have no device, so recent work is verified by unit tests,
JVM renders and the desktop scan benchmark only. Several rows say "not compiled" because their session had no Android
SDK; everything up to F215 has since been built and unit-tested (full local run on F205, `6bb662b`: 1,055 tests,
0 failed, 2 skipped, lint 0 errors). F216 was not in that run (R85).

## 1. Needs the owner

1. **Say what is going wrong with scanning (R64, R81).** The 2026-11-18 report (unreliable capture, poor quality, bad
   filters, poor detection, "all tools broken") came without examples and no fault was reproduced. Send three or four
   photos of the same page taken with the phone's own camera, say what went wrong, and name the tools that failed.
2. **Decide R50:** drop the held-back permissions change (`git stash drop` on the Mac) or say what it was meant to recover.
3. **File the Play Console "All files access" declaration (R70, R53).** Draft: `PLAY_ALL_FILES_ACCESS.md`. Without it an
   update that asks for the access at first launch may be rejected.
4. **Decide R59:** should photographing a document shown on a screen auto-capture?
5. **Privacy policy:** fill the highlighted fields in `docs/privacy-policy/index.html`, host it at a public HTTPS URL and
   set `scanpro.privacyPolicyUrl=<url>` in `gradle.properties` (not set as of 2026-11-19), so the Settings link appears.
6. **A native speaker's read** of the Hindi, Kannada, Malayalam, Tamil and Telugu strings written by an assistant
   (R19, R36, R44, R52, R53, R69); start with `TRANSLATION_REVIEW.md`. The F188 strings are English only in those five (R65).
7. **Release signing** has never been validated; builds are unsigned as configured.
8. **Undo history across sessions:** design written, nothing built (`UNDO_PERSISTENCE_DESIGN.md`); it waits on three
   answers (keep up to 20 earlier text states in plain app-private storage, the limits, and a Recycle-bin document's history).
9. **GitHub housekeeping (checked 2026-11-19):** Dependabot pull requests #15 (Gradle wrapper 9.9.0) and #16 (ZXing core
   3.5.4) are still open, but the branch already has both versions, so they can be closed. Branch `ccr-d3174e53-3zuive`
   still exists and can be deleted. Pull request #18 from the working branch is open; `main` was last updated on 2026-11-11.
10. **On the Mac only (a cloud session cannot see them):** the R50 stash, and an uncommitted 3° rotation in
    `res/layout/widget_scan.xml` that looks like an accidental drag.

## 2. Open items by priority

Judge smoothness on a release build (`AGENTS.md`). Where a check fails, name the tool and the place.

### P1

- **R81** (F213; Device check). Page detection changes (ID card, open book, steadier outline) were measured on the desktop benchmark only; the owner's report was not reproduced on a phone.
  - Next: On the phone, release build: (1) an ID card on wood, on a dark desk and in the hand, in Document mode and ID card mode: outline on the whole card, not below its header band; ID card mode saves the card alone when it is outlined, the guide's contents otherwise; (2) an open book: one steady outline, no flipping between one page and the spread; (3) a page lying still: outline does not tremble, does not blink off on a blurred frame, and follows a moved phone without lag; (4) `ScanProAnalyzer` frame rate still smooth (colour edges and the wider straight-edge search add about 1.5 to 4 ms a frame on the desktop). If anything is still wrong, ask for 3 or 4 photos of it from the phone's own camera and run them through `scripts/scan-bench`.
- **R80** (F210–F212; Device check). Page, convert and edit tool upgrades pass their unit tests in CI; none of the screens has been seen on a phone.
  - Next: Run one tool of each group end to end on a real PDF; check the estimate against the result; Redact text on a file with comments and form fields, then search the output for the words in a desktop PDF reader.
- **R71** (F194, F198, F199, F204, F205; Device check). Edit PDF text: the engine is tested on the desktop; the editor and its page preview have not been seen on a phone.
  - Next: On the phone: Tools › Edit PDF on a text PDF (a bill or a letter from Word): tap a line, change a word, Apply (the page should redraw with the change in the same font), change bold, size and colour, add new text with Add text (on an upright and a turned page), Save, open the result and search for the old word (it should not be found). Also a PDF made by a phone scanner app (often a picture: the editor should say there's no text) and a rotated page. F205: pinch to zoom and drag the page, then tap a small line (the right line should open); Find and replace a word that is on several pages; open Changes, remove one, tap another to jump to its page; Undo and Redo; Move a line with the arrows; Save with your own file name and check the file is called that.
- **R70** (F195–F197; Device check / Play policy). First-launch permission page, widget, tool demos, tilting cards and the result screen have not been seen on a phone; Play needs the All files access declaration.
  - Next: (1) Fresh install: the welcome permission page lists Files on your phone; Allow asks camera and notifications, then opens All files access; coming back opens the app; Not now opens it too. An existing install with no access sees the one-time Files prompt once, before What's new and the tour. (2) Play Console: asking for All files access at start needs the MANAGE_EXTERNAL_STORAGE declaration (document management); without it the update may be rejected. (3) Widget: add it, resize through 2 cells, one row, two rows; every button opens the right place; colours follow the wallpaper on Android 12+, readable in dark mode. (4) Tool demos: How it works card and introductions play the scene; the stage flips in; with animations off a still frame shows. (5) Cards tilt towards the finger on Tools and Files (check the direction feels right) without stopping scrolling or long press. (6) Result screen shows Original → Result and the page count.
- **R69** (F193; Device check). Rebuilt image tools (Compress, Convert, Resize, Crop) have not been seen on a phone; their estimates are unchecked against real results.
  - Next: Check each image tool with a 12 MP camera JPG, a transparent PNG and a screenshot: Compress (size presets only below the file size; the Result preview and Now → After row update within about half a second; the result keeps 4000 px when the target allows; a missed target says so), Convert (default format, "already a PNG" disables the button, transparency notice, several pictures with the same name all in the zip, per-picture crop), Resize (Fit by default, Fit/Fill/Stretch only when the shape differs, enlarging warning, PNG stays PNG), Crop (corners, edges, move, each ratio, Rotate, Mirror, Reset, pixel size; PNG keeps transparency). Estimates come from a 1280 px sample and are marked "About"; compare them with the real results. New strings in five languages are unreviewed machine translations. Smoothness of the live estimate on a slow phone is unmeasured.
- **R64** (F181–F186; Device check). Capture, filter and detection rework after the owner's 2026-11-18 report (unreliable capture, poor saved quality, bad filters, poor detection, then "all tools broken", without examples): measured on the desktop only, no fault reproduced.
  - Next: On the phone: (1) scan a page on a wooden desk with a shadow across it: the blue outline should hold the whole page; (2) compare a saved page before and after (small print should be sharper; a page is now about 2600 to 3300 px long, so about 70% more storage per page and a longer save); (3) each filter on a text page, a page with a logo or photo, and a page under a warm lamp; (4) Invert PDF on a text PDF; (5) shutter delay with the quality capture mode (F186), and frame rate and heat in the camera over a minute (each frame now also makes a colour map). If scans are still poor, the owner sends three or four photos of the same page taken with the phone's own camera, and says what went wrong, so the detector and filters can be run on them (`scripts/scan-bench`).
- **R60** (F179; Device check). The overhaul fixes compile and pass the unit suite; not seen on a phone.
  - Next: On the phone: open the camera from Home (no blank flash), Home after a cold start (hero not shrinking, beam stops), rotate a page in a document (no Stop row), Tools twice (Quick access in place), QR mode in landscape (outlines on the codes), delete a captured page then capture (no Undo beside "captured"), sign a PDF with a dot, crop handles size, viewer search keyboard, Split on a damaged PDF, Settings > a page > back keeps the scroll.
- **R2** (`targetSdk = 37`; Compat). Raised to Android 17 after a code review; not yet run on an Android 17 device, tablet or foldable.
  - Next: Device-test on Android 17, including a large screen.
- **R7** (whole app; Testing). No full device pass; `ScreenScreenshotTest` renders screens but asserts nothing; camera, PDF viewer and PDF tools are not rendered.
  - Next: Device pass; Compose UI tests in CI on an emulator; golden comparison for the screenshots.
- **R44** (F134–F136; Testing / Release). QR scanner, Sign PDF, PDF to text or Word and Insert pages: CI only, never tried on a device, no UI tests; strings unread by a speaker.
  - Next: Try on a phone: several codes in one frame, history, generator round trip with another scanner, signing a rotated PDF, text and OCR conversion of a scanned PDF, insert pages with a protected second PDF. Have a speaker read the new strings.
- **R50** (held-back change; Privacy / Owner decision). A change adding photo and storage permissions and launch-time recovery over external storage is held back in a `git stash` on the owner's Mac.
  - Next: Owner decides: drop it (`git stash drop`), or say what it was meant to recover.

### P2

- **R83** (F215; Device check). Small items (notification Cancel, launcher shortcuts, signed-PDF notice, translation download question, PDF scratch cap) are compiled and unit-tested in CI only.
  - Next: On the phone: (1) start a long Compress or Merge, pull down the notification, tap Cancel: the tool stops with "cancelled" and the notification goes; the same for a queued document edit; (2) long-press the launcher icon: Scan now, Bugs or issues and Don't remove still work (after one app launch on an upgraded install); (3) Sign PDF on a digitally signed PDF shows the notice; (4) Translate into a new language asks once before downloading; (5) a very large honest PDF (200+ MB) still runs through Compress and Merge (scratch cap).
- **R82** (F214; Look check). The decluttered Tools tab, PDF tool pages and scan review have been compiled in CI only; nobody has seen them.
  - Next: On the phone or with `ToolsLandingScreenshotTest`: each Tools panel shows two rows and Show all (n); a tool page reads file, main choice, estimate, button, with More options and How it works folded; review ⋮ menu holds whole-scan actions only; 48 dp crop and reorder controls; large text and dark mode.
- **R68** (F192; Device check). Protect PDF with a file that already needs a password: not seen on a phone.
  - Next: Open Protect PDF with a file that needs a password (e.g. `scanpro-test protected.pdf`): the notice shows and no password fields; Remove password first switches the same page to Unlock. With a plain PDF the fields show after a brief check; with a PDF that only has editing restrictions the fields show.
- **R66** (F189, F190; Device check). The tool progress card and the press-and-hold demos have not been seen on a phone.
  - Next: F189: run Merge, Compress, OCR and a tool that never reports a count (e.g. Unlock); the card must keep its size when the % appears, the ring must ease from circling into filling, no glow cut at the edges (Images to PDF veil), badge centred under the page, tick at 100%; check light and dark and on a tablet or in landscape (card capped at 360dp). F190: the press-and-hold demo in Merge, Organise pages and Images to PDF plays three times, then holds a mid-drag frame; still with animations off; legible at 1.3x font size.
- **R65** (F188; Device check). The hexagon colour picker and "Save for next time" in Sign PDF have not been seen on a phone; their strings are English only in the five other languages.
  - Next: Check: Sign PDF > Signature and > Text or date, tap the rainbow "+" swatch, drag over the hexagons, type a hex code, Use colour; the chosen colour shows in the swatch row; Save for next time reads on one line at 1.3x font size; new strings are English only in the five other languages (fall back to English) and need translating.
- **R67** (F191; Device check). Files' storage overview, Folders view, grid view and the cloud picker have not been seen on a phone.
  - Next: On the phone: Files › On this phone: the counts, Downloads, Processed files (lands on Tools' results), Folders in and out (and the back button), grid and list, and Open from Google Drive with a PDF, a photo and a Word file.
- **R61** (2026-11-18 UI audit; UI / UX). Findings left out of F179 because they are riskier or need a device to tune (camera, review, viewer, tabs); the full list is in the R61 row.
  - Next: Fix one at a time on a device, each with a before/after recording.
- **R56** (F168–F170, F175, F177; Device check). Hard-shadow and framed-object detection were measured with the desktop benchmark only; auto-capture on shadowed pages needs a watch for wrong captures.
  - Next: Run the device benchmark (see `BUILD_AND_TEST.md`), scan a real page with a hand's shadow across it on a wooden desk, and try auto-capture over a patterned mat and a busy desk; point the live camera at a laptop and a keyboard (F177 should show "Point at a document" and not capture) and at a page with a printed dark border (should still capture).
- **R55** (F165, F166; Device check). Camera open and close, and tool smoothness, were fixed from a screen recording and code reading; nothing was measured.
  - Next: On the phone that recorded it: open the camera from Home five times (cold and warm), close by X, by back button and by back gesture, with and without captured pages; watch for a black gap, a freeze, a shrinking card and hint flicker. If the open still stalls, record a Perfetto trace of the bind. F166: scroll a long PDF quickly in Organise, run Merge and Compress on large files, and open Files with many documents on a RELEASE build; a debug build of a Compose app is several times slower, so judge smoothness on release. If a tool still stutters, capture a Perfetto or `adb shell dumpsys gfxinfo` trace and name the tool.
- **R53** (F160–F163; Device check / Play). Files on this phone, the default-viewer and rating sheets, the issue form and the scan guides have not run on a phone; strings are machine-written; Play needs the All files access declaration.
  - Next: On a phone: grant and revoke All files access (Android 11+ and 10), Files on this phone with real Downloads/WhatsApp folders, default-viewer sheet, rating sheet after scans 1 and 2 and the email it opens, issue form with screenshots, How to scan → Try now, Book A | B swap order in saved pages, ID card art. Fill the Play declaration. Speaker's read (R19).
- **R52** (F165–F167; Device check). Settings, QR and PDF tools changes of 2026-11-16 were checked only with renders and unit tests; strings are machine-written.
  - Next: On a phone: pin widget and shortcut, Open by default; QR batch mode, beep, gallery save, CSV share, date picker; each new PDF tool on a real and a protected PDF (blank pages and greyscale use PdfRenderer, not covered by tests); the PDF info dialog. Speaker's read of the strings (R19).
- **R19** (themes, tips, What's new; UX). Verified by JVM renders only; the Hindi, Telugu, Kannada, Tamil and Malayalam strings have not been read by native speakers.
  - Next: Device pass through each colour theme in light and dark; native-speaker review of `strings_themes_tips.xml`.
- **R24** (launch animation; UX). Verified by renders and launch tests, not on a device.
  - Next: On a device: cold start from the launcher (Android 12+ and 11), app lock on, rotation during the animation, a share into the app (no animation).
- **R25** (`scanner/PageQuality.kt`; UX). The blur, glare and darkness limits come from the live detector and synthetic tests, not from real photos.
  - Next: Run `SCAN_QUALITY_TEST_PLAN.md` photos through `OpenCvScanProcessor.assessQuality` and tune `GLARE_MIN_SHARE` / the clipped level.
- **R36** (F64, F65; Testing / UX). Whether OCR has finished when the "Scan saved" sheet opens is unverified; none of F64/F65 has run on a phone. (The row's unfinished suite and lint run has been overtaken by later full runs.)
  - Next: On a device scan a receipt, a business card and a letter and check the sheet; then the R24 and R25 device checks.
- **R43** (F101; Testing). "Recover missing documents" and the moved-aside library dialog have not been tried on a device; if a newer build left changes only in the WAL file, Room can throw on open.
  - Next: Try "Recover missing documents" on a device after moving a database aside; downgrade then upgrade and check documents come back without duplicates.

### P3

- **R85** (F216; Device check (F216)). About ScanPro page and the More group were written without an Android SDK: not compiled there, not run, not seen.
  - Next: CI compiles it and `SettingsPagesScreenshotTest` opens the new page (it walks every `SettingsPage`); look at the page on a phone in light, dark and Hindi.
- **R84** (F213; Known limit). Known limit: a card filling ID card mode's guide on a grained desk loses its whole-card outline in about a third of noisy frames (`OutlineTracker` hides most of it).
  - Next: Needs real camera frames before more tuning; a straight-edge search for the enclosing outline when the winner touches a strong inner edge is the likely fix.
- **R72** (F200, F203; Look check). The regrouped Tools tab looks right in renders (light, dark, Hindi); not yet seen on a phone.
  - Next: On the phone: Tools in light and dark, large text (two wide cards per row), the other four languages.
- **R63** (Sign PDF text dialog; Tools / UI). Left after F201: whether the colour picker (F188) reads well with TalkBack.
  - Next: Check with TalkBack on a phone.
- **R59** (F177, F178; Limitation / owner decision). Measured limits of the framed-object check: full-bleed dark frames are held back from auto-capture, a silver laptop on a darker desk is still taken for a page, a document shown on a screen is held back.
  - Next: Decide (3); check (1) and (2) with real objects on the phone before changing anything.

## 3. Carried over from the 2026-11-08 and 2026-11-11 lists

These are not rows in the Open table. They were open when written and were **not re-checked on 2026-11-19**; none is
recorded as done.

**Device checks**
1. Camera work, all device-only: fast save and review-when-needed (F68), ID card both sides on one page (F67), camera
   strip with long-press delete and Undo (F74), duplicate-aware auto-capture (F75), Book mode two-page spreads and the
   spine guide (F80), the Scan tile and home-screen widget (F78); tablet side rail Scan button (F52).
2. Large-font and landscape pass (F83): Welcome and Lock on a tablet or at the largest font, the crop editor's two
   button rows, the Scan saved sheet with the keyboard open, the Tools progress card with Cancel and Back.
3. Safety and access pass (F82, F84): create a backup and a protected PDF (typed twice, show/hide), Share on a Wi-Fi code
   (no password), a calendar code (start, end), TalkBack on the sliders, 48 dp targets, a failed translation.
4. Camera after a rotation (F86): open the camera, turn the phone, take a page in each orientation; no page may be saved
   sideways and the outline must still match the page.
5. Report an issue (F86): Settings > Report an issue opens the mail draft without a pause, the attached
   `scanpro_issue_report.txt` has no folder paths or file names, and the draft says what it contains.
6. Compress to a target size (F86): choose a target well below the file's size and check the result is at or under it,
   or as close as the file allows.
7. Layout fixes from the screenshots (F89): at font scale 2 the Settings pickers are radio rows and no word breaks; in
   landscape at font scale 2 all four rail tabs fit without jumping; the landscape camera panel controls show whole;
   the landscape Adjust page filter thumbnails are readable; in Tamil the bottom bar, rail and document tabs fit on one
   line (Tamil wrapped a tab mid-word on the last device pass); long status subtitles scroll smoothly; landscape dialogs
   stay within the screen.
8. Images to PDF and Recents (F90, F92): pick three photos, rotate one, crop one, add more, rotate all, revert; rotation
   keeps crops and order; the staging cache is cleared on dismiss, cancel and completion. Open a PDF from a cloud or
   Downloads provider, close the app completely, reopen it from Recent and confirm the pages show; delete the file and
   tap its row to see the message.
9. Camera switching (F91): switching between Camera and Files is quick, with no frame held longer than 250 ms.

**Not started (from the owner's list)**
10. Keep OCR word boxes in backups: after a restore, Excel export falls back to plain lines.
11. Offer OCR on documents brought back by "Recover missing documents": recovered pages have no text.
12. QR ideas: product barcodes and ISBN with "open in my browser" (no online lookup, offline-first); live text mode
    (highlight, copy, call, translate).
13. Tool ideas: table to Excel, receipt and expense summary, batch tools. (Redact PDF, also on this list, was built in
    F172 and F174.)

**Known gaps and limits**
14. Multi-code QR outlines assume the preview and analysis share one 4:3 field of view with FIT_CENTER (also in R44).
15. `upload-artifact` v7 (Dependabot bump) had not run when this was written: those steps only run on a failed build or
    a manual run.

## 4. Decisions already made by the owner (2026-11-08)

- **R20, documents and database encryption at rest:** not changed. Documents, OCR text and the database rely on Android
  file-based encryption and the sandbox; app lock and locked folders are screen locks, not encryption.
- **R21, exported activity internal actions:** not changed, low risk.
- **R22, crash report file names:** not changed, low risk; crash reports stay on the device unless the user shares them.
- **Screenshots in debug builds:** kept. `MainActivity` skips `FLAG_SECURE` when `BuildConfig.DEBUG`; release builds set it.
- **More languages:** Marathi, Gujarati, Bengali and Punjabi are deliberately not added.

## 5. PDF Tools backlog

See `TOOLS_IMPROVEMENTS.md`.

Fixed items and their evidence are in the "Fixed" table of `AUDIT_FINDINGS.md`; build and test results in `BUILD_AND_TEST.md`.
