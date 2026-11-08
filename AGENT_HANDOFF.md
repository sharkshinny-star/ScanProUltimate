# Agent handoff

Read this first when you pick up the project in a new session or with a different model. Then read, as needed:

| File | What it holds |
|---|---|
| `AGENT_HANDOFF.md` (this file) | Where things stand, what was done, what is open, what to do next |
| `BUILD_AND_TEST.md` | How to build and test, the last results, known build problems |
| `AUDIT_FINDINGS.md` | Every bug, security issue and fix, with status and evidence |
| `ARCHITECTURE.md` | Modules, packages, dependencies and the reasons behind key decisions |

**Keep these four files current.** After any significant change (a fix, a feature, a dependency bump, a build or test run),
update the affected files in the same commit as the change. `CLAUDE.md` repeats this rule so every agent session sees it.

Last updated: 2026-11-08. Today, three threads: **F66/F67/F68** (committed: launch animation and theme/language hand-over; ID card both sides on one page; fast save from the camera, closing R26) and, **uncommitted in this working tree**, F69 (torch bug fixed, one top-bar flash control closes R31, result-first review closes R27; seen on the phone) and F70 (device layout pass on a OnePlus CPH2569: nav bar, landscape rail, 150 % font, launch icon and launch feel; R38 opened). Careful: the merge `2dc8429` and a stale-editor save had dropped the F66–F68 rows and this file's updates; restored by hand, so trust `git log` over older copies of these docs.

## 1. Current state

- **Branch:** `claude/awesome-clarke-ussjo3` on `codingking155/ScanProUltimate`. Work is pushed; no pull request is open.
- **Merged into it:** `main`, `claude/adoring-euler-5s8pn0`, `claude/compassionate-turing-m8ylcw`, `claude/sweet-mccarthy-jajb5j`, by hand on 2026-10-30/31; their commits were recorded in this branch's history on 2026-11-05 by a history-only merge (`-s ours`, no file changes), so `git rev-list HEAD..origin/<branch>` is empty for every branch.
  The other branches add nothing beyond this one. Conflicts were resolved by hand, keeping the more advanced side or combining both.
- **Builds (latest full run, see `BUILD_AND_TEST.md` "Latest results"):** 320 unit tests, 0 failed; lint 0 issues; bundle and sideload release builds pass. That run predates F65: the full unit-test and lint run on the current tree is still owed (R36). Last 16 KB alignment check: 2026-11-04, `b76720c`.
- **Not verified:** on a device, only this (OnePlus CPH2569, Android 15, 2026-11-08): `AppSmokeTest` passes; the F69/F70 fixes were seen working (flash/torch, result-first review, layout at 150 % font and landscape, launch icon). Camera end-to-end saving, PDF tools, app lock, notifications and backup/restore
  are otherwise untested in practice. Camera, PDF tools, app lock, notifications and backup/restore
  are untested in practice. No instrumented tests beyond `AppSmokeTest`; `ScreenScreenshotTest` (opt-in) renders 12 screens
  in light and dark for review but asserts nothing.
- **Figma review board:** "ScanPro UI review (real app screens)", https://www.figma.com/design/iHjKny6T0cokWhVhglDkTB, the owner's Starter-plan file
  (View seat: Figma MCP reads are capped at about 20 a month; writes such as `html_to_figma` are not). After UI changes,
  re-render and upload a new board (the boards are plain layers from `html_to_figma`, not linked to code).
- **App version:** 4.2.0 (versionCode 7), `applicationId com.scanpro.app`, minSdk 24, targetSdk 37, compileSdk 37.

## 2. Completed work (newest first)

| Commit | What |
|---|---|
| not committed | F73: Compress PDF Target Size Validation & Result Dialog polish: fixed bug where entering target size >= current file size produced invalid expected size (> original); added inline error state and prominent warning banner; disables Compress PDF confirm button when target >= current size; engine-level validation in `CompressPdfEngine.compressPdf`; aligned estimate pills with uniform right-hand boundaries and `maxLines = 1, softWrap = false` to prevent text-wrap collisions; completely overhauled ResultDialog with hero comparison card (`Original` ➔ `Compressed`, `% smaller` badge), full-width tonal Preview PDF button, grouped circular Share/Save quick actions with centered labels, and full-width Done button; full 6-locale strings (`values`, `values-hi`, `values-kn`, `values-ml`, `values-ta`, `values-te`). Verified on OnePlus device (`dc6b0551`). `PdfToolsFeaturesTest` (7 passed). |
| not committed | F72: PDF Tools upgrades: "Extract" renamed to "Extract Pages" across 6 languages; single-page PDF guard displays "Cannot extract pages" notification and enforces `userRequire(doc.numberOfPages > 1)`; Compress PDF shows live expected size estimates per preset (~65% High, ~40% Balanced, ~20% Maximum) and dynamic summary badge; added 4th option for Custom Target Size (KB/MB) with dynamic scaling/quality calculation; rebuilt ProcessingOverlay as a Dialog with BackHandler, non-repeating labels, page-by-page progress (`Compressing page X of Y...`), and smooth indeterminate indicator. `PdfToolsFeaturesTest` (6). |
| not committed | F71: Settings Issue Reporting with Automated Diagnostics: tapping "Report an issue" auto-collects application/build info, hardware/OS platform, display/locale, storage/memory metrics, recent crash report, in-memory event trail (`AppDiagnostics`), and system logcat; compiles and attaches `scanpro_issue_report.txt` via FileProvider with `FLAG_GRANT_READ_URI_PERMISSION` alongside structured summary in body; updated subtitles across all 6 locales; `AppDiagnosticsTest` (6). Tests: 328 unit tests passing, 0 lint errors. |
| not committed | F69: torch bug fixed (the toggle wrote a flag the flash effect never watched); one top-bar flash button (Off/Auto/On/Torch, amber when lit, remembered between scans); review opens on the finished page with Crop · Filters · Adjust tools (closes R27, R31). Verified on the OnePlus. |
| not committed | F70: device layout pass (OnePlus, Android 15): navigation bar no longer hidden, landscape side rail fits, 150 % font fixes (bottom bar, search pill, hero, stat tiles), launch-icon brackets inside the system's circle; launch no longer feels stuck (the system icon keeps scanning while the app loads; `LaunchIntro` starts at 0.60 s). R38 opened (Home scrolls itself ~14 dp). |
| `c27ad4b` | F68 (closes R26): fast save from the camera. `ReviewMode` setting (Always / If needed / Never, default If needed) in Settings › Scanning; `FastSave` decides from the per-page crop and quality; `CameraScannerScreen.reviewOrSave` saves on Done or opens the review on the first page that needs a look. `FastSaveTest` (4); 342 tests 0 failed; lint 0 errors. |
| `3888dd2` | Instrumented tests for the launch, the in-place language change and ID cards, to run from Android Studio (written, not run). |
| `a502f3b` | F67: ID card mode defaults to Both sides; Done after the second side composes one page (front at the top, back below, white A4 shape, `IdCardComposer`) before the review; Retake there re-shoots both sides. `IdCardComposerTest` (2). 338 tests 0 failed, lint 0 errors. |
| `d97deab` | F66 from the owner's screen recording: launch animation 2.25 s (was 1.6 s), wordmark held, indigo-azure-teal flood; the intro follows the app's theme and `MainActivity` sets the system's per-app night mode so the Android 12+ launch screen matches from the next start; `configChanges="locale\|layoutDirection\|uiMode"` so the language picker's Continue (and theme changes) no longer restart the activity (the black frame). CI heaps capped for the free runner; tests 336/0, lint 0 errors. |
| next | `docs/archive/`: old ScanPro repo's `FirebaseCloudClient.kt` kept as `.txt` (not compiled, API key redacted) for possible future cloud sync. Old ScanPro repo compared: nothing else worth porting. |
| `0284287` | `TOOLS_IMPROVEMENTS.md`: PDF Tools backlog (7 items, related R7/R28, device checks for F48–F50). |
| `ef2f3b2` | History-only merge of `main` and the three older branches (`-s ours`, no file changes). |
| `5221a67` | `REMAINING_WORK.md`: every open item and the device checklist in one file. |
| `1dfa430` | Review fix for F48 (picture that won't decode is named as an image again); uiautomator 2.4.0 → 2.4.1 (owner request), lint 0. Results in `BUILD_AND_TEST.md`. |
| `959ce27` | F65: "Scan saved" sheet like the leading scanners: Searchable PDF badge, smart action (business card → Add to contacts, receipt/invoice → total + Copy total), Share as PDF / Copy text / Add pages, Scan another. `ScanSavedSheetTest` (5). Full suite and lint not re-run (R36). |
| `f93c43c` | Competitor-comparison leftovers recorded as R26–R35. |
| `e7385fe` | F64: every scanned page is checked for blur, glare and darkness; the review flags it with Retake and marks its thumbnail; the camera hints at glare. `PageQualityTest`, `ScanReviewQualityTest`. |
| `0d74480` | F63: review sliders fold into an Adjust panel (Filters / Adjust switch, bigger page area); mode-aware automatic names. |
| `1623d68` | F62: share a selection as one PDF; Add page › Scan with camera (pages keep the review's crops and filters). 317 tests, lint 0. |
| `263325d` | Launch animation built from the Figma design (`ui/LaunchIntro.kt`, static `ic_launch_icon` launch screen replaces the animated `ic_launcher_splash`); `LaunchIntroTest`. |
| `20465bb` | Owner decision recorded: keep Android 7–10 (minSdk 24) despite the partial task-hijacking fix (F58). |
| `7eee16f` | F61: ⋮ quick menu on document cards; Scan from a Files folder files into it; Text-mode scans open on the Text tab. 310 tests, lint 0. |
| `d867f5a` | Security hardening pass (owner asked to make the app "hack proof"). Backups: 8-character minimum and 600k PBKDF2 rounds for new backups, old 6-character/210k backups still restore (F53); restore caps on file size, entry count and manifest size (F55). PDF password minimum 8 (F54). Kept-scan and restored annotation paths confined (F56). Password keyboard type on every password field (F57). `taskAffinity=""` against task hijacking (F58). Gradle checksum pinned, wrapper JAR verified, keystores git-ignored (F59). `google()` content-filtered (F60; cloud dependency downloads ~40x faster). Backup crypto reviewed (V6, closes R6); attack surface incl. Office conversion, WebView, QR/UPI and locked folders reviewed (V7). New open items R20 (no encryption at rest, owner decision), R21, R22, R23 (ML Kit usage metrics to disclose). |
| `10fa630` | Colour themes (5 palettes, Settings > Appearance), "Did you know?" Home tips, one-time What's new sheet, animated scan beam on the empty-library hero card, F52 (tablet rail Scan button), translations of the new strings (R19), `ColourThemeScreenshotTest`. A launch-animation redesign was drawn in Figma but not shipped (see `ARCHITECTURE.md` 12b). |
| `e66c106` | F51: choose the folder a new scan is saved in, on the review screen (Name and Folder cells; shared folder picker). |
| `b7681d7` | F50: first-launch permission page after the welcome (Camera, Notifications on 13+), `WelcomePermissionsTest` (5). |
| `22540d1` | F48/F49: Images to PDF decodes once on white (transparent PNGs no longer black); Merge asks for a protected file's password. `PdfPictureTest`, `MergePasswordTest`. Also fixed two lint errors in `ScanReviewReorderTest` (state created in composition). |
| `b6362d3` | F47: fixes from an independent review of F44/F45 (text-dialog drafts survive rotation app-wide; strip no longer jumps or lags while dragging; drag finds its page by key). |
| `08d9887` | F46 (from the competitor comparison): slider Reset and Apply to all pages, Rotate all pages, Auto capture in the camera ⋮ menu, spoken camera guidance. |
| `c21d582` | F45: name a scan on the review screen before saving (Name row and dialog; the name rides the import request into the document). |
| `5f96824` | F44: drag-to-reorder on the review thumbnail strip (long-press, lift, swap; accessibility actions); `ScanReviewReorderTest` (3). |
| `b76720c` | F43: search results show the page text around a text-only match; Recycle bin actions under the notice; short file sizes; dimmed end arrows in the page grid. `ab4e72d`: landscape review keeps Save visible; 0 compiler warnings. Full run: 275 tests (0 failed), lint 0, both release builds, 16 KB alignment. |
| `94b09d9` | `ScreenScreenshotTest` (12 screens × light/dark, Robolectric, opt-in) reviewed in Figma; F41 dark-mode content colour app-wide (Scan review, Lock, Welcome); F42 Settings stat tiles and even segmented selectors; R18 suggestions. 267 tests, lint 0. |
| `bd3cacd` | Verified F38 to F40 with a real build; Office notes warnings. |
| `07293b0` | F40: mode hints and an explained mode lock; thumbnail strip and one filter rule in the review; 40 to 48 dp touch targets, pen swatch ring, scrolling dialogs (R14, R16). |
| next | F39: localised fallback titles, Office-to-PDF notes and file sizes (R13); camera and review screens lay out in landscape (R15). |
| next | F38: editors confirm discard and keep state across rotation; "Save all (n)" and Delete-page confirmation in the scan review; pinch zoom, Auto/Manual label, tick on the finish button; background scans open only from a top-level screen. |
| e7b141e | F37: four start-up bugs fixed: crash when the Scan shortcut or a notification arrives while the first-run language picker is up; Recents replaying a shared file or shortcut; 1 s frozen frame on every rotation (Android 12+); welcome could flash the library first. Recents regression test added. |
| b61d381 | ML Kit text-recognition (Play services) 19.0.1→19.0.2 and the four script modules 16.0.1→16.0.2; every other ML Kit library already latest. Compile, 229 unit tests, lint, both release builds pass; not checked on a device. |
| c4542a6 | Robolectric launch test on API 24–37 (all pass). |
| 3b497d4 | Office to PDF (F36); P3 complete. |
| 5f27eba | Review fixes (F34) and P3 Settings defaults (F35). |
| 3ab433d | Hotfix F33: launch crash on Android 7–12 from the language code. |
| 6dd0557 | Lens-style QR mode (F32): viewfinder, scan from a picture, Wi-Fi Connect, UPI pay. |
| bc1aecd | Kannada and Malayalam (same checks as F31; `kn`/`ml` added to `locales_config.xml` and `AppLocale.SUPPORTED`). |
| ca8bfa8 | P2 (F31): Hindi/Tamil/Telugu UI, first-launch language picker and in-app language setting, freehand pen, locked folders. |
| cca9dcf | Scanner, Crop-and-filters and Document screens rebuilt to the owner's second design artifact (F30); per-page brightness/contrast/saturation now travel from the review into the import. |
| 0344fbd | Rating sheet from the owner's design (F29): once, after the first saved document; Play listing for 4–5 stars, feedback email below. |
| 89f0eae | P1 declutter after the Play Store comparison (F28): Home, Camera, Review, Tools, Welcome, Document bar. |
| 6101b4e | Sharper PDF viewer, organiser layout, Manual capture default, app name ScanPro (F27). |
| feaf266 | Document screen decluttered, instant document opening, ID card guide crops to the card (F26). |
| 9bb0629 | Adaptive Auto Clean (F25); signature placement step (drag, pinch/corner resize, Save). |
| c068e53 | Owner screenshot fixes (F24): animated launch icon, night greeting, smaller home scan card, no white band under the bottom bar, adaptive tool grid, filtered review preview, Done button no longer clipped. |
| a merge | Merged owner commit `540a492` (older local copy): kept its version catalog (`gradle/libs.versions.toml`, used by `:translate`, `:baselineprofile`), `gradle.properties` parallel sync, `gradle-daemon-jvm.properties`, renamed scope file; undid its reverts (code, targetSdk 37, BouncyCastle pin, newer deps), removed `app/google-services.json` (offline-first) and the IDE report files; `.gitignore` now blocks both. |
| 001b98a | IDE inspection report (F23): more OpenCV exclusions, non-deprecated rotate icons, `Duration` timeout, British spelling throughout (owner decision). |
| same commit | Review fixes: edit queue order, Stop/journal cleanup, skipped-edit message, journal fsync (F21); HEIC detection, page limit, translatable kept-scan reasons, empty Retry, old cache sessions adopted, long-image/ID-copy export (F22). |
| bd18aea | Removed the unused `server/` placeholder and its README section (F20, closes R8). |
| `4d8947a` | Backup dialogs warn that backups keep unredacted originals (F19, closes R1). |
| `ecc322b` | Gallery picks that are unreadable, HEIC on Android 8, or over the page limit are reported instead of dropped silently (F18, closes R10). |
| `36cbaa2` | Remaining English strings localised: `UserFacingException` for errors, ~120 strings (F17, closes R5). |
| `b165003` | Queued document edits written to a journal and replayed after a restart (F16, closes R3). |
| `abdce28` | `CLAUDE.md`: every session works on this branch. |
| `c62088d` | "Unsaved scans" recovery screen for scans whose import failed (F15, closes R4); Home banner, notification deep link, `KeptScanStoreTest`. |
| `fb9e745` | targetSdk 37 after an Android 17 code review; OpenCV excluded from lint and IDE inspections (shared `.idea` scopes, profile, dictionary); remaining Kotlin style hints applied. |
| `8b9cc77` | These reference docs and `CLAUDE.md`. |
| `65d2c5c` | Local notifications (finished/failed tasks, optional recycle-bin and backup reminders, Settings > Notifications). PDF tools held alive by the foreground service via `OperationRunner.keepAlive`. |
| `b3b7613` | Dependency updates (AGP 9.5.0, Kotlin 2.4.21, KSP 2.3.13, Gradle 9.8.2, DataStore 1.3.0, coroutines 1.11.0). BouncyCastle constrained to 1.86. Dead code and compiler warnings removed. |
| `f4d2483` | Lint: ~30 strings to plurals, 80 unused resources removed, Compose primitive state, `LocalWindowInfo` window size, KTX helpers. |
| `09bb6b8` | PDF viewer: evicted pages are no longer recycled while on screen; cache cleared when the file changes. |
| `bfe815d` | Camera sessions moved from `cacheDir` to `filesDir`; `.pending_import` marker; automatic import retries; `CacheCleanerTest`. |
| `cb748f2` | Fixed the only compile error left by the merge (`steadyGuidance` on the `Guidance` enum). |
| `7e543d5` | Recycle-bin purge uses the user's retention setting instead of a fixed 7 days. |
| `3c37a28` | Merge of `claude/compassionate-turing-m8ylcw` (~240 conflict hunks, ~230 strings merged, `UiMessage` callback fixes). |

Details per finding: `AUDIT_FINDINGS.md`.

## 3. Unresolved issues

Ranked; IDs match `AUDIT_FINDINGS.md`.

1. **Device testing (required before release).** Includes F48 (transparent PNG to PDF), F49 (merge with a protected PDF) and F50 (fresh install: welcome, permission page, both answers, Android 12 and 13+). Camera, every PDF tool, app lock, notifications, backup/restore, on at least one
   low-end and one recent phone.
2. **Release signing** has never been validated; builds are unsigned as configured. Never print signing secrets in logs.
3. **R2: targetSdk is now 37** (code-reviewed; launches under Robolectric on API 24–37, not yet on a device). Test on Android 17, including a tablet or foldable.
4. **F38, F39 and F40 compile, pass the 243 unit tests and lint (0 issues) on `07293b0`, but have never run on a device**: check on a device that Back in the editors asks to discard, rotating keeps mark-up, "Save all (n)" saves from any page, Delete page asks, pinch zoom works, a scan finished while editing another document announces itself instead of opening. For F39 also check both screens in landscape, a new scan's title in Hindi, and the sizes in the library. For F40 also check the review's thumbnail strip, mode hints and the locked-mode message, and the dialogs at 200% font. Still open from the UX review (`UX_REVIEW.md`): R17 (contrast measurements; drag-to-reorder was done in F44).
5. **R18: UX suggestions** from the screenshot review (document title truncation, "System default", folder on grid cards; the Recycle bin top bar was fixed in F43). Owner decides; see the Figma board.
6. **R20: no encryption at rest.** The library relies on Android's file-based encryption and the sandbox; app lock and locked
   folders are UI gates. On a rooted or forensically extracted phone, documents are readable. Fixing it (Keystore-backed
   encryption of page files and the database) is architectural: ask the owner and document it first.
7. **R23: ML Kit usage metrics.** ML Kit's logging transport ships in the release build and the app holds `INTERNET`; Google says
   the APIs report usage and performance metrics (no content). Declare it in the Play Data safety form and the final policy.
8. **Security changes not device-tested:** password keyboards (F57), launcher/shortcut/notification/share launches with
   `taskAffinity=""` (F58; owner decided 2026-11-05 to keep Android 7–10, so no `minSdk 30`), backup speed with 600k rounds on a low-end phone (F53). R21 and R22 are low priority.

## 4. Next steps

All open work in one list, with a device checklist: `REMAINING_WORK.md` (generated from `AUDIT_FINDINGS.md`).

- PDF Tools backlog (password prompts in every tool, file names, page numbers/watermark, sign, PDF to Word, insert pages): `TOOLS_IMPROVEMENTS.md`.
1. **Left over (2026-11-05):** (a 2026-11-05 attempt could not build: `dl.google.com` blocked by the environment's network policy, see `BUILD_AND_TEST.md` Known errors) run the full `:app:testDebugUnitTest` and `:app:lintDebug` (stopped before finishing for
   F65, R36); device pass of the launch animation (R24), page-quality limits on real photos (R25) and the "Scan saved"
   sheet with a receipt, a business card and a letter (R36); native-speaker check of the new strings (R19); the
   competitor leftovers R26-R35.
2. Competitor-comparison leftovers, open as R26 to R35 in `AUDIT_FINDINGS.md`, roughly in value order: fast save from the camera (R26), review shows the finished page with crop on request (R27), Share PDF card with size estimates (R28), Book mode spread split (R29), cropped camera thumbnails with quick delete (R30), one flash button (R31, owner decision), duplicate-aware auto-capture (R32), Quick Settings tile and widget (R33), localised smart titles and a name pattern (R34), tags, sort and recent searches (R35). Already done from the same comparison: F44-F47, F51, F61-F63.
3. Remaining from the owner's Play Store comparison: four more languages (Marathi, Gujarati, Bengali, Punjabi: same brief and `check_translation.py` as F31, then add each to `locales_config.xml` and `AppLocale.SUPPORTED`); P3 done (PowerPoint export dropped at the owner's request).
4. Run on a device or emulator; record results in `BUILD_AND_TEST.md`.
5. Try Office to PDF on a device with a real .docx, .xlsx and a page saved from Chrome (⋮ › Download).
6. **Do this before the other device checks: launch on an Android 12-or-older device or emulator (F33 hotfix), including picking a language; also F37 (Scan shortcut before choosing a language, reopen from Recents after sharing a PDF, rotate on Android 12+).** Then device-check F32 with real codes: a Wi-Fi QR (Connect on Android 11+, password copy below), a UPI QR (opens UPI apps), an event/ticket QR, a screenshot from the gallery. Then F31: first launch language picker (pick Hindi, Tamil, Telugu; Android 12 and 13+), Settings > App language, the pen (colours, widths, move in Edit mark-up), lock a folder (hidden from All/Recent/search, unlock prompt, re-locks 30 s after leaving), and have native speakers read the translations. Then F30 on the owner's phone: scanner chrome (zoom, torch, mode strip, Auto/count pill), the Adjust page screen (handles, sliders, Save page), the Document tabs, per-page sheet and Export sheet; then F29 (rating sheet after the first scan: stars, emoji, both buttons) and F28 (every screen changed) and F24–F26 on the owner's phone: document screen sheets and pager, opening from Recent, ID card capture crop, Auto Clean on real documents, signature placement; and F24 (three-button navigation, large display size): launch animation, bottom bar, tool sheet, review screen.
7. Device-check F21/F22: tap Stop on an edit (no "interrupted" message after restart), rapid filter taps keep order, HEIC pick message.
8. Device-check the backup warning (F19) on a redacted document and in Settings > Back up all.
9. Device-test "Unsaved scans": force an import failure, check the banner, notification tap, Save again, Share photos, Discard.
10. Device-test the edit journal: queue several edits behind a long OCR, force-stop the app, reopen, check they run and that the interrupted one is reported.
11. Compose UI tests on an emulator in CI; extend `ScreenScreenshotTest` to Tools, PDF viewer and dialogs, and add golden comparison.
12. Device-test targetSdk 37 on Android 17 (R2).

## 5. Rules carried over from the owner's audit brief

- Inspect before changing; never claim a test passed without running it.
- No network services, analytics, accounts, FCM or tracking. Notifications are local only.
- Never silently delete user data; don't weaken security for convenience; don't remove features without a reason.
- Document architectural, destructive-migration, feature-removal and security-sensitive changes before making them.
- Don't expose signing credentials.
- Push only to `claude/awesome-clarke-ussjo3`, even if the session was assigned another branch (see `CLAUDE.md`); don't open a pull request unless asked.

## 6. Session environment notes

These live outside the repo; a new cloud container won't have them.

- Android SDK was installed at `/opt/android-sdk` (cmdline-tools 16111833, build-tools 37.0.0, platform `android-37.0`; the newer SDK manager names platform 37 `platforms;android-37.0`), licences accepted at the owner's request on 2026-11-01. `local.properties` (gitignored) points at it.
- Maven Central returned HTTP 429, so `~/.gradle/init.d/central-mirror.gradle` routed it to Google's Maven Central mirror.
  Recreate it if dependency downloads fail with 429; see `BUILD_AND_TEST.md`.
