# Remaining work

Everything still open on `claude/awesome-clarke-ussjo3`. Section 2 is written by `scripts/remaining-work.py` from the
"Open" table in `AUDIT_FINDINGS.md`, which holds the full problem description and evidence for each ID and is the
source of truth. After changing that table, run `python3 -I scripts/remaining-work.py`. The other sections are written
by hand (last reviewed 2026-11-19, after F217 and F205).

Nearly every item is a check on a phone: cloud sessions have no device, so recent work is verified by unit tests,
JVM renders and the desktop scan benchmark only. Several rows say "not compiled" because their session had no Android
SDK; everything up to F217 has since been built and unit-tested in one of two full local runs, each with 0 failed and
lint 0 errors: F205 (`6bb662b`, 1,055 tests) and F217 (`8d6bf7b`, 1,053 tests, which includes F216). No run of
the two trees merged is recorded.

## 1. Needs the owner

1. **Say what is going wrong with scanning (R64, R81).** The 2026-11-18 report (unreliable capture, poor quality, bad
   filters, poor detection, "all tools broken") came without examples and no fault was reproduced. Send three or four
   photos of the same page taken with the phone's own camera, say what went wrong, and name the tools that failed.
2. **Decide R50:** drop the held-back permissions change (`git stash drop` on the Mac) or say what it was meant to recover.
3. **Decided 2026-11-19 (F225): the Play version ships without All files access**, so no declaration is needed; build the bundle with
   `-Pscanpro.allFilesAccess=false` (the build stops without it). Only if the access is put back: file the Play Console "All files access" declaration (R70, R53). Draft: `PLAY_ALL_FILES_ACCESS.md`. Without it an
   update that asks for the access at first launch may be rejected.
4. **Decide R59:** should photographing a document shown on a screen auto-capture?
5. **Privacy policy (fields filled in F218):** add the developer name in the two highlighted places of
   `docs/privacy-policy/index.html` and delete the yellow box, host it at `https://sharkshinny-star.github.io/scanpro-privacy/`
   (new public repo `scanpro-privacy`, GitHub Pages on) and
   set `scanpro.privacyPolicyUrl=<url>` in `gradle.properties` (not set as of 2026-11-19), so the Settings link appears.
6. **A native speaker's read** of the Hindi, Kannada, Malayalam, Tamil and Telugu strings written by an assistant
   (R19, R36, R44, R52, R53, R69); start with `TRANSLATION_REVIEW.md`. The F188 strings are English only in those five (R65).
7. **Release signing** has never been validated; builds are unsigned as configured. On the Mac run
   `bash scripts/setup-upload-key.sh` (F220: makes the upload key outside the repo and sets the `SCANPRO_*` values in
   `~/.gradle/gradle.properties`), then build the bundle and check it is signed.
8. **Undo history across sessions:** design written, nothing built (`UNDO_PERSISTENCE_DESIGN.md`); it waits on three
   answers (keep up to 20 earlier text states in plain app-private storage, the limits, and a Recycle-bin document's history).
9. **GitHub housekeeping (checked 2026-11-19):** Dependabot pull requests #15 (Gradle wrapper 9.9.0) and #16 (ZXing core
   3.5.4) were closed unmerged on 2026-11-19 at the owner's request: the working branch already has both versions.
   `main` still has Gradle 9.8.2 and ZXing 3.5.3 until the working branch is merged (pull request #18, open; `main` was
   last updated on 2026-11-11). Two branches are left for the owner to delete: `ccr-d3174e53-3zuive` (its tip `6f383c8`
   is in the working branch) and `dependabot/gradle/gradle-wrapper-9.9.0`. A cloud session cannot: GitHub answers a
   branch delete from it with HTTP 403. Dependabot removed its ZXing branch itself.
10. **On the Mac only (a cloud session cannot see them):** the R50 stash, and an uncommitted 3° rotation in
    `res/layout/widget_scan.xml` that looks like an accidental drag.

## 2. Open items by priority

Judge smoothness on a release build (`AGENTS.md`). Where a check fails, name the tool and the place.

<!-- BEGIN open-items: written by scripts/remaining-work.py from AUDIT_FINDINGS.md; do not edit by hand -->

40 open items: 14 P1, 19 P2, 7 P3.

### P1

- **R92** (Owner + device check (F243); `imaging/metadata/`, `feature/tools/FileInfoScreen.kt`, `THIRD_PARTY_NOTICES.md`). File info and metadata was not compiled in the cloud (no Android SDK): only its pure parts ran (7 JUnit tests). jaudiotagger is LGPL 2.1.
  - Next: Owner: confirm the LGPL terms are acceptable for the Play build (licence text in the app's notices, source offer), or drop audio tag writing. On a phone: open a camera photo with location (see GPS, Remove location, check the copy in a map app shows none and is not turned sideways), a DOCX (edit title, open the copy in Word), an MP3 (edit artist; Android 7 shows read only), an MP4, a ZIP, a HEIC and a RAW; Export JSON and CSV; confirm the release build (R8) still reads audio tags and XMP.
- **R88** (Device check; F222–F224). The 2026-11-19 animation, layout, rating and How-it-works changes were checked with rendered screens and unit tests only; no frame of motion was seen.
  - Next: On a phone, release build: switch tabs and watch the highlight move; in the review of a 3-page scan switch Crop, Filters, Adjust, Reorder (page glides, no jump), in portrait and landscape; pick each filter (no cut border); open the camera from Home's QR code and Book pills (mode underlined and in view); save a scan and rate it with 5, then (Settings > Rate) with 2 stars (thanks at once; problem form, email opens with the rating line); after Clear data, open Merge and Compress (How it works open the first time, folded the second).
- **R81** (Device check; F213). Detection changes measured on the desktop benchmark only (generated scenes, no real camera frames); the owner's report (paper, book, ID card, jumpy outline) was not reproduced on a phone.
  - Next: On the phone, release build: (1) an ID card on wood, on a dark desk and in the hand, in Document mode and ID card mode: outline on the whole card, not below its header band; ID card mode saves the card alone when it is outlined, the guide's contents otherwise; (2) an open book: one steady outline, no flipping between one page and the spread; (3) a page lying still: outline does not tremble, does not blink off on a blurred frame, and follows a moved phone without lag; (4) `ScanProAnalyzer` frame rate still smooth (colour edges and the wider straight-edge search add about 1.5 to 4 ms a frame on the desktop). If anything is still wrong, ask for 3 or 4 photos of it from the phone's own camera and run them through `scripts/scan-bench`.
- **R80** (Device check; F210–F212). Page, convert and edit tool upgrades: unit tests pass in CI run 219; none of the screens has been seen on a phone.
  - Next: Run one tool of each group end to end on a real PDF; check the estimate against the result; Redact text on a file with comments and form fields, then search the output for the words in a desktop PDF reader.
- **R73** (Device check; F206). Two engine changes can only be judged on a phone with real PDFs: lines moving down under text that grew, and Indian scripts drawn as shapes.
  - Next: On the phone: (1) in a letter or bill, replace one line of a paragraph with a long sentence, with Text wrapping on Auto: the lines under it should move down with their look unchanged, and stop at the next gap; check a page with a table or picture under the paragraph (those do not move). (2) Type a line in Hindi, Kannada, Malayalam, Tamil or Telugu: the letters should be joined as in any other app, in the page preview and in the saved PDF opened in another viewer. If either looks wrong, say which PDF and what it did.
- **R71** (Device check; F194, F198, F199, F204, F205, F206, F207). Edit PDF text was written in the cloud container: the engine is tested on the desktop, the editor and the Android session (PdfRenderer preview) are not compiled or seen.
  - Next: On the phone: Tools › Edit PDF on a text PDF (a bill or a letter from Word): tap a line, change a word, Apply (the page should redraw with the change in the same font), change bold, size and colour, add new text with Add text (on an upright and a turned page), Save, open the result and search for the old word (it should not be found). Also a PDF made by a phone scanner app (often a picture: the editor should say there's no text) and a rotated page. F205: pinch to zoom and drag the page, then tap a small line (the right line should open); Find and replace a word that is on several pages; open Changes, remove one, tap another to jump to its page; Undo and Redo; Move a line with the arrows; Save with your own file name and check the file is called that. F206 (the editor screen was rebuilt, so repeat the checks above in it): tap a line (it is selected and a small bar appears), tap it again (its words open), drag it (it moves and stays there); use each tab of the bottom panel and each sheet once and check Apply changes the page; add text, then tap it again and change it; Done, name the file, open the saved PDF. F207: in Font and style choose Roboto, Inter, Gelasio and Montserrat in turn (regular, bold, italic): the page should show each, and the saved PDF should look the same in another viewer.
- **R70** (Device check / Play policy; F195–F197). Not seen on a phone.
  - Next: (1) Fresh install: the welcome permission page lists Files on your phone; Allow asks camera and notifications, then opens All files access; coming back opens the app; Not now opens it too. An existing install with no access sees the one-time Files prompt once, before What's new and the tour. (2) Play Console: no declaration needed while the Play bundle is built without All files access (owner decision 2026-11-19, F225); it is needed again if the access is put back. (3) Widget: add it, resize through 2 cells, one row, two rows; every button opens the right place; colours follow the wallpaper on Android 12+, readable in dark mode. (4) Tool demos: How it works card and introductions play the scene; the stage flips in; with animations off a still frame shows. (5) Cards tilt towards the finger on Tools and Files (check the direction feels right) without stopping scrolling or long press. (6) Result screen shows Original → Result and the page count.
- **R69** (Device check; F193). Not compiled and not seen on a phone (no Android SDK in the cloud container); three new Robolectric tests in `ImageToolsEngineTest` not run.
  - Next: Check each image tool with a 12 MP camera JPG, a transparent PNG and a screenshot: Compress (size presets only below the file size; the Result preview and Now → After row update within about half a second; the result keeps 4000 px when the target allows; a missed target says so), Convert (default format, "already a PNG" disables the button, transparency notice, several pictures with the same name all in the zip, per-picture crop), Resize (Fit by default, Fit/Fill/Stretch only when the shape differs, enlarging warning, PNG stays PNG), Crop (corners, edges, move, each ratio, Rotate, Mirror, Reset, pixel size; PNG keeps transparency). Estimates come from a 1280 px sample and are marked "About"; compare them with the real results. New strings in five languages are unreviewed machine translations. Smoothness of the live estimate on a slow phone is unmeasured.
- **R64** (Device check; F181–F186). Written in the cloud container: the filters, `LiveChroma` and the detector were run on the desktop (see `BUILD_AND_TEST.md`), the Android side was not compiled there and nothing was seen on a phone.
  - Next: On the phone: (1) scan a page on a wooden desk with a shadow across it: the blue outline should hold the whole page; (2) compare a saved page before and after (small print should be sharper; a page is now about 2600 to 3300 px long, so about 70% more storage per page and a longer save); (3) each filter on a text page, a page with a logo or photo, and a page under a warm lamp; (4) Invert PDF on a text PDF; (5) shutter delay with the quality capture mode (F186), and frame rate and heat in the camera over a minute (each frame now also makes a colour map). If scans are still poor, the owner sends three or four photos of the same page taken with the phone's own camera, and says what went wrong, so the detector and filters can be run on them (`scripts/scan-bench`).
- **R60** (Device check; F179). The overhaul fixes compile and pass the unit suite but were not seen on a phone.
  - Next: On the phone: open the camera from Home (no blank flash), Home after a cold start (hero not shrinking, beam stops), rotate a page in a document (no Stop row), Tools twice (Quick access in place), QR mode in landscape (outlines on the codes), delete a captured page then capture (no Undo beside "captured"), sign a PDF with a dot, crop handles size, viewer search keyboard, Split on a damaged PDF, Settings > a page > back keeps the scroll.
- **R2** (Compat; `app/build.gradle.kts` `targetSdk = 37`). Raised to 37 (Android 17) after a code review against the Android 17 behaviour changes (see F12). Still needs a run on an Android 17 device or emulator before release: camera and editor on a tablet or foldable (orientation and aspect-ratio locks are ignored there), the foreground service during… (more in the row)
  - Next: Device-test on Android 17, including a large screen.
- **R7** (Testing; whole app). Nothing verified on a device. `ScreenScreenshotTest` (Robolectric, opt-in) renders 12 screens in light and dark for review but asserts nothing; camera, PDF viewer and PDF tools are not rendered.
  - Next: Device pass; Compose UI tests in CI on an emulator; golden comparison for the screenshots.
- **R44** (Testing / Release; QR scanner (`scanner/ScannedCode.kt`, `QrOverlay.kt`, `feature/qr/*`, `data/ScanHistoryStore.kt`), …). F134 to F136 compiled and passed their unit tests and lint in CI run 120 (`35bbe79`), but nothing has been tried on a device, and the new screens have no UI test.
  - Next: Try on a phone: several codes in one frame, history, generator round trip with another scanner, signing a rotated PDF, text and OCR conversion of a scanned PDF, insert pages with a protected second PDF. Have a speaker read the new strings.
- **R50** (Privacy / Owner decision; `AndroidManifest.xml`, `AppViewModel.kt`, …). An uncommitted change added `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED` and `READ_EXTERNAL_STORAGE`, and ran document recovery at every launch over external storage.
  - Next: Held back on 2026-11-15: the permissions are never requested, contradict the no-storage-permission design and the Data safety form, and launch-time recovery can race an unfinished import. Owner decides: drop it, or say what it was meant to recover.

### P2

- **R90** (Device check / Play; F232 (drink-water reminders)). The reworked reminders ran only as unit tests of the schedule; the notice, its two buttons and the Settings rows were never seen on a phone.
  - Next: On a phone: reminders arrive at about the set times and survive a restart; "I drank" and "Skip" put the notice away without opening the app; changing a timing moves the next reminder; the three rows hide when the switch is off; the hour labels follow the 12/24-hour setting. In Play Console: answer the Health apps declaration as described in `PLAY_STORE_CHECKLIST.md` section 3.
- **R87** (Device check (F221); `core/designsystem/Components.kt` (`TaskBanner`), …). The task banner and the bar above it were looked at as two still renders only, never moving or on a phone. The Files change (no search, sort or grid switch in the top bar while On this phone shows) compiles and the unit suite passes, but was not rendered or seen.
  - Next: On a phone: start a long task (Re-read text on a long document, or import a large PDF) and look at Home, Files and the document screen: after 0.7 s the banner slides open with the task's words and Stop, the bar sweeps without an empty moment, Stop stops the task and the banner closes. Files › On this phone: the top bar shows the title and the menu only; My documents still has search, sort and the grid switch.
- **R86** (Device check (F219); `feature/tools/ProcessingOverlay.kt`, `core/designsystem/LoadingAnimations.kt`, …). The progress card's new layout, arrival, two-second minimum and finish were checked with unit tests and JVM renders only (eleven still frames looked at).
  - Next: On a phone, release build: (1) a quick tool on a small PDF (Rotate, Protect, Compress): the card stays about two seconds, the ring closes to 100% with the figure counting up, the tick is drawn, "Done" stands where Cancel was, then the result shows; say whether two seconds feels right when several quick tools are run one after another; (2) Merge several large files: real progress, then the short 0.7 s finish; (3) Cancel and Back still ask while the tool works and do nothing during the finish; (4) the card is centred upright, shows the dial beside the text with the phone on its side, and is larger on a tablet; largest font size; (5) with animations turned off (Developer options) the card neither arrives nor waits; (6) Images to PDF: the small veil animation (light behind the page) still looks right.
- **R83** (Device check; F215). Small items compiled and unit-tested in CI only.
  - Next: On the phone: (1) start a long Compress or Merge, pull down the notification, tap Cancel: the tool stops with "cancelled" and the notification goes; the same for a queued document edit; (2) long-press the launcher icon: Scan now, Bugs or issues and Don't remove still work (after one app launch on an upgraded install); (3) Sign PDF on a digitally signed PDF shows the notice; (4) Translate into a new language asks once before downloading; (5) a very large honest PDF (200+ MB) still runs through Compress and Merge (scratch cap).
- **R82** (Look check; F214). The decluttered Tools tab, PDF tool pages and scan review compiled in CI only; nobody has seen them.
  - Next: On the phone or with `ToolsLandingScreenshotTest`: each Tools panel shows two rows and Show all (n); a tool page reads file, main choice, estimate, button, with More options and How it works folded; review ⋮ menu holds whole-scan actions only; 48 dp crop and reorder controls; large text and dark mode.
- **R68** (Device check; F192). Not compiled and not seen on a phone.
  - Next: Open Protect PDF with a file that needs a password (e.g. `scanpro-test protected.pdf`): the notice shows and no password fields; Remove password first switches the same page to Unlock. With a plain PDF the fields show after a brief check; with a PDF that only has editing restrictions the fields show.
- **R66** (Device check; F189, F190). Not compiled and not seen on a phone.
  - Next: F189: run Merge, Compress, OCR and a tool that never reports a count (e.g. Unlock); the card must keep its size when the % appears, the ring must ease from circling into filling, no glow cut at the edges (Images to PDF veil), badge centred under the page, tick at 100%; check light and dark and on a tablet or in landscape (card capped at 360dp). F190: the press-and-hold demo in Merge, Organise pages and Images to PDF plays three times, then holds a mid-drag frame; still with animations off; legible at 1.3x font size.
- **R65** (Device check; F188). Written in the cloud container (no Android SDK or Gradle mirror there): the hexagon colour picker and the Save for next time row were never compiled or seen on a phone.
  - Next: Check: Sign PDF > Signature and > Text or date, tap the rainbow "+" swatch, drag over the hexagons, type a hex code, Use colour; the chosen colour shows in the swatch row; Save for next time reads on one line at 1.3x font size; new strings are English only in the five other languages (fall back to English) and need translating.
- **R67** (Device check; F191). Files' storage overview, Folders view, grid view and the cloud picker were written in the cloud container and not compiled there.
  - Next: On the phone: Files › On this phone: the counts, Downloads, Processed files (lands on Tools' results), Folders in and out (and the back button), grid and list, and Open from Google Drive with a PDF, a photo and a Word file.
- **R61** (UI / UX; camera, review, viewer, tabs (audit 2026-11-18)). Audit findings not fixed in F179 (riskier or need a device to tune): camera guidance pill and corner dots flicker with `stable` (needs a hold like `GuidanceHold`), ID-card guide oversize in landscape, zoom/exposure sliders and preview insets read in composition, retake label vs hint pill overlap… (more in the row)
  - Next: Fix one at a time on a device, each with a before/after recording.
- **R56** (Device check; F168–F170 (`ScanDetectionBenchmarkTest`)). The hard-shadow fixes (and F175's colour source) were measured only with the desktop benchmark; the device benchmark (which now asserts twelve scenes, seven with shadows) and a real shadowed page have not been tried on a phone.
  - Next: Run the device benchmark (see `BUILD_AND_TEST.md`), scan a real page with a hand's shadow across it on a wooden desk, and try auto-capture over a patterned mat and a busy desk; point the live camera at a laptop and a keyboard (F177 should show "Point at a document" and not capture) and at a page with a printed dark border (should still capture).
- **R55** (Device check; F165, F166 (`SMOOTHNESS_CHECK.md`; Play declaration draft in `PLAY_ALL_FILES_ACCESS.md`)). Camera open and close smoothness was fixed from a screen recording and code reading only.
  - Next: On the phone that recorded it: open the camera from Home five times (cold and warm), close by X, by back button and by back gesture, with and without captured pages; watch for a black gap, a freeze, a shrinking card and hint flicker. If the open still stalls, record a Perfetto trace of the bind. F166: scroll a long PDF quickly in Organise, run Merge and Compress on large files, and open Files with many documents on a RELEASE build; a debug build of a Compose app is several times slower, so judge smoothness on release. If a tool still stutters, capture a Perfetto or `adb shell dumpsys gfxinfo` trace and name the tool.
- **R53** (Device check / Play; F160–F163). Nothing of 2026-11-16's second round ran on a phone; new strings in five languages are machine-written; Play Console needs the All files access declaration only if the access is put back in the Play build (F225).
  - Next: On a phone: grant and revoke All files access (Android 11+ and 10), Files on this phone with real Downloads/WhatsApp folders, default-viewer sheet, rating sheet after scans 1 and 2 and the email it opens, issue form with screenshots, How to scan → Try now, Book A | B swap order in saved pages, ID card art. No Play declaration while the Play bundle has no All files access (F225). Speaker's read (R19).
- **R52** (Device check; F165–F167). Settings, QR and PDF tools changes of 2026-11-16 were checked only with Robolectric renders and unit tests; 87 + new tool + settings strings in five languages are machine-written.
  - Next: On a phone: pin widget and shortcut, Open by default; QR batch mode, beep, gallery save, CSV share, date picker; each new PDF tool on a real and a protected PDF (blank pages and greyscale use PdfRenderer, not covered by tests); the PDF info dialog. Speaker's read of the strings (R19).
- **R19** (UX; `core/designsystem/Palettes.kt`, `core/designsystem/PalettePicker.kt`, …). Colour themes, Home tips and the What's new sheet are verified only by JVM renders (`ColourThemeScreenshotTest`) and tests.
  - Next: Device pass through each colour theme in light and dark; native-speaker review of `strings_themes_tips.xml`.
- **R24** (UX; `ui/LaunchIntro.kt`, `res/drawable/ic_launch_icon.xml`, `MainActivity.kt`). The new launch animation is verified by JVM renders compared frame by frame with the Figma render, `LaunchIntroTest` (timing, tap to skip) and `LaunchSmokeTest` (56 launches, Android 7-17), not on a device.
  - Next: On a device: cold start from the launcher (Android 12+ and 11), app lock on, rotation during the animation, a share into the app (no animation).
- **R25** (UX; `scanner/PageQuality.kt`). The blur, glare and darkness limits come from the live detector and synthetic tests, not from real photos. Glossy covers, white-on-white pages and IDs with holograms may be flagged too often or too rarely.
  - Next: Run `SCAN_QUALITY_TEST_PLAN.md` photos through `OpenCvScanProcessor.assessQuality` and tune `GLARE_MIN_SHARE` / the clipped level.
- **R36** (Testing / UX; `feature/document/ScanSavedSheet.kt`, `scanner/PageQuality.kt`, `ui/LaunchIntro.kt`). Left over from the 2026-11-05 after-scan, page-quality and launch work: (1) the full `:app:testDebugUnitTest` + `:app:lintDebug` run for F65 was stopped by the owner before it finished; only compile and `ScanSavedSheetTest` (5) ran; (2) whether OCR has finished when the "Scan saved" sheet opens is… (more in the row)
  - Next: Run the full suite and lint; on a device scan a receipt, a business card and a letter and check the sheet; then R24/R25 device checks.
- **R43** (Testing; `data/LibraryRecovery.kt`, `data/DocumentDatabase.kt` (`setAsideNewerDatabase`), …). F101 is unverified on a device: the recovery action and the moved-aside database have not been tried on one. CI is green on the single-job workflow (run 117, `698d7bc`), and `setAsideNewerDatabase`, `reOcrDocument`, `appendImages`, `splitBookSpread` and `importEncryptedBackup` have unit tests… (more in the row)
  - Next: Re-run CI; try "Recover missing documents" on a device after moving a database aside.

### P3

- **R91** (Device check (F234); `feature/library/LibraryScreen.kt`, `feature/phonepdfs/PhonePdfsScreen.kt`). The search pill in Files' top bar and the lower bar were not rendered or seen on a phone.
  - Next: On a phone, Files > On this phone: the pill sits beside "My files" and is not cramped at large font sizes or in the longer translations of the title; typing filters the list and the cursor behaves; the clear button works; the pill hides while files are selected; My documents and Search still look right with the 56 dp bar.
- **R89** (Device check (F227, F233); `core/designsystem/HeaderScenes.kt`). The header scenes were looked at as still frames only. Their motion, speed, size in the real top bar (about 52 dp high) and cost in frames and battery were not measured: the strip redraws every frame while Home is open and a scene plays.
  - Next: Watch Home on a release build on a phone; if it stutters or distracts, lengthen `REST_MILLIS`, or stop after a few scenes per visit. Ask the owner which scenes to keep.
- **R85** (Device check (F216, F217); `feature/settings/SettingsAboutPage.kt`, `SettingsMainPage.kt`, …). The About page, the Settings main page (library figures, named groups) and the Files controls and storage tiles were compiled, unit-tested and rendered by Robolectric (light and dark), not seen on a phone.
  - Next: Look at Settings and Files on a phone in light, dark and one Indian language; have a native speaker read the new strings.
- **R84** (Known limit; F213, `scripts/scan-bench` scene `k_card_in_guide_wood`). A card filling ID card mode's guide on a grained desk: in about a third of noisy frames the whole-card outline is not found (its contour joins the wood grain and reaches the frame edge), so the detector returns the card's face.
  - Next: Needs real camera frames before more tuning; a straight-edge search for the enclosing outline when the winner touches a strong inner edge is the likely fix.
- **R72** (Look check; F200, F203). The regrouped Tools tab was rendered (light, dark, Hindi) and looks right there; not yet seen on a phone.
  - Next: On the phone: Tools in light and dark, large text (two wide cards per row), the other four languages.
- **R63** (Tools / UI; `feature/tools/SignPdfScreen.kt` (`SignTextDialog`)). Mostly closed by F201 (HD switch, font names, unused strings, `.artifacts/`). Left: whether the Sign text dialog's colour picker (F188) reads well with TalkBack, with R65.
  - Next: Check with TalkBack on a phone.
- **R59** (Limitation / owner decision; `scanner/PageFraming.kt` (F177)). Measured limits of the framed-object check (F178): (1) a dark frame printed right at the paper's edge (full-bleed certificates) is held back from auto-capture; (2) a silver laptop on a darker desk is still taken for a page; (3) a tablet or monitor showing a document is held back.
  - Next: Decide (3); check (1) and (2) with real objects on the phone before changing anything.

<!-- END open-items -->

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
