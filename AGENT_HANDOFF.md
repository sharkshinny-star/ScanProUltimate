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

Last updated: 2026-11-09 (F95: Camera exit blank screen fix with TextureView COMPATIBLE mode and cold start launch intro animation race/hang fix; F94: Polish touch target sizing, button padding, permission panel layout; F93: Polish launch transitions, crop decoding resilience, review autosizing; F92: Images to PDF feature audit and overall improvements).

## 1. Current state

- **Branch:** `claude/awesome-clarke-ussjo3` on `codingking155/ScanProUltimate`. Fully up to date with origin at commit `9690ffa`.
- **Merged into it:** `main`, `claude/adoring-euler-5s8pn0`, `claude/compassionate-turing-m8ylcw`, `claude/sweet-mccarthy-jajb5j`, by hand on 2026-10-30/31; their commits were recorded in this branch's history on 2026-11-05 by a history-only merge (`-s ours`, no file changes), so `git rev-list HEAD..origin/<branch>` is empty for every branch.
  The other branches add nothing beyond this one. Conflicts were resolved by hand, keeping the more advanced side or combining both.
- **Builds (latest full run, see `BUILD_AND_TEST.md` "Latest results"):** 405 unit tests (0 failed, 63 skipped); `connectedDebugAndroidTest` on physical OnePlus CPH2569 (6/6 passed); lint 0 errors, 1 warning (Gradle 9.9.0 notice).
- **Device verification (OnePlus CPH2569, Android 15, 2026-11-08):** `connectedDebugAndroidTest` 6/6 passed (`AppSmokeTest`, `LanguageChangeDeviceTest`, `LaunchDeviceTest` x2, `IdCardDeviceTest` x2); R42 search pill clipping checked across 10 tab transitions (no clipping); F81 refactor smoke test verified (scan, filter edit, undo, delete page, merge 2 documents, share PDF, empty Recycle bin); R38 rotation state verified (camera adjustments, Pen, PDF viewer search, merge dialog); document tab labels in Hindi, Tamil and Telugu verified fitting cleanly without truncation.
- **Figma review board:** "ScanPro UI review (real app screens)", https://www.figma.com/design/iHjKny6T0cokWhVhglDkTB, the owner's Starter-plan file
  (View seat: Figma MCP reads are capped at about 20 a month; writes such as `html_to_figma` are not). After UI changes,
  re-render and upload a new board (the boards are plain layers from `html_to_figma`, not linked to code).
- **App version:** 4.2.0 (versionCode 7), `applicationId com.scanpro.app`, minSdk 24, targetSdk 37, compileSdk 37.

## 2. Completed work (newest first)

| Commit | What |
|---|---|
| next | F95: Camera exit blank screen and cold start launch intro animation fix: Reverted `PreviewView` to `COMPATIBLE` (TextureView) so live preview frames smoothly slide out with Compose controls on back press without punching a black hole; removed blocking `unbindAll()` in `onDispose`; resolved Compose race condition during app lock loading in `MainActivity.kt` that skipped `LaunchIntro`; immediate splash screen dismissal (`provider.remove()`) when intro is active; 350ms safety timeout on splash fade to prevent ColorOS/OnePlus `withEndAction` animator hangs; added missing `sizeIn` import in `DocumentScreen.kt`. |
| next | F94: Touch target sizing, button padding, and permission panel polish: minimum 48dp on camera `BottomPill` and document `MoveButton`, `fillMaxWidth` on permission panel actions, consistent `contentPadding` on document Add/Split/Merge buttons, and `TextOverflow.Ellipsis` on tool grid card labels. |
| next | F93: Launch transitions, crop decoding resilience, and review autosizing: smooth splash screen handover, fallback paths and multi-tier downsampling (2000, 1400, 1000 px) in `CropEditorDialog` with error fallback screen, `.jpg` source extension in `DocumentImporter` and `PageOrganiser`, responsive `FlowRow` in `HomeTips` and `EmptyLibrary`, and `TextAutoSize` in `QuickActions` and `ScanReviewScreen`. |
| next | F92: Comprehensive Images to PDF audit and overhaul. Eliminated disk cache leak (`img2pdf_order_*` staging directories purged on dismiss, cancel, `finally` block in `imagesToPdf`, and startup scan); preserved ordering and edit state across rotation (R38) via `rememberSaveable` and `OrderedImageListSaver`; implemented `rotateImageFile` in `ImageProcessor` and wired per-card "Rotate" and bulk "Rotate all" actions; added "+ Add images" launcher; added "Reset" action to revert crops and rotations while cleaning intermediate files; redesigned `OrderedImageRow` into a spacious two-row card with position badge, 60dp thumbnail with edited indicator, unconstrained filename, and separate actions row; added landscape dialog height capping and `FlowRow` for page size/quality filter chips; translated strings in 5 Indic languages; added unit test in `PdfToolsFeaturesTest`. |
| next | F91: Fixed Camera <-> Files switching freeze/hang. Replaced ON_RESUME and post-layout re-bindings with explicit retryTrigger, switched PreviewView to zero-copy PERFORMANCE (SurfaceView), cached session disk cleanup per process, guaranteed immediate unbindAll and executor shutdown on disposal. Verified on physical OnePlus CPH2569 across 10-cycle automated test: 1.7s and 2.1s freezes completely eliminated (0 frames > 250ms), 90th percentile 28ms, 95th percentile 40ms, 50th percentile 12ms, 0 HAL configuration failures during active binds. |
| next | F90 (Images to PDF crop, Recents reopening): `RecentPdfStoreTest` (4). Images to PDF crop runs on `Dispatchers.IO` with progress spinner and error display. Recents keeps private local copy in `filesDir/recent_pdfs` when URI permission is transient; row readability verified before opening. Privacy policy section 2 updated. Not seen on a device. |
| next | Device checklist & smoke tests pass: connectedDebugAndroidTest (6/6 passed on OnePlus CPH2569); R42 search pill clipping verified absent across 10 tab transitions; F81 refactor smoke test verified (scan, filter edit, undo, delete page, merge, share PDF, empty Recycle bin); R38 rotation state verified (camera adjustments, Pen, PDF viewer search, merge dialog); document tab labels in Hindi, Tamil and Telugu verified fitting cleanly on device under per-app locales. Closes R38 and R42. |
| next | F89 closes R43-R46 (layout problems seen in the device screenshots): `SettingsChoiceRow` radio rows above ~115% font scale; compact rail labels with uniform height (no jumping on selection); symmetrical centred landscape camera controls; Auto detect and Rotate scrolling in the landscape review column; `FitText` for the bottom bar, rail and document tabs; `basicMarquee()` for long status subtitles in `TitleWithStatus`; landscape dialog height caps in `OrderFilesDialog` (240 dp) and Document merge dialog (220 dp). `FitTextTest` (3). Not seen on a device. |
| next | F88 closes R23: the owner submitted the Play Data safety form from the draft in `PLAY_STORE_CHECKLIST.md` (Diagnostics, Other app performance data, Device or other IDs; collected, required, Analytics). The form is in Play Console, so what was entered is the owner's record; the choice on ticking Shared is not recorded. Privacy policy hosting remains. |
| next | F87: model review of the 105 strings added since F64 in all five languages (`TRANSLATION_REVIEW.md`): English tokens inside sentences, second words for page/document/compress/size, a wrong Telugu verb and a Tamil ambiguity fixed; judgement calls listed for a native speaker. Draft Play Data safety answers added to `PLAY_STORE_CHECKLIST.md` from Google's ML Kit disclosure page. |
| next | F86: review follow-ups for the commits made on the owner's machine: camera follows the screen rotation (the activity no longer restarts), issue report built in the background with paths, links and file names removed, compress-to-size checks the result and squeezes again, recent PDFs cannot be overwritten by an early add, three unused strings removed, Home scroll safety net re-arms each visit. `AppDiagnosticsRedactTest` (5). Not seen on a device. |
| next | F85 closes R18: Recycle bin actions on small phones. "Empty" moved from the button row under the notice into the TopAppBar ⋮ dropdown menu in `TrashScreen.kt`, and "Restore all" spans full width below retention notice. |
| next | F82-F84 close R40, R39, R41: passwords typed twice when created (backup, protected PDF) with show/hide; Welcome, Lock, crop editor, Scan saved sheet and Tools progress card fixed for tablets, large fonts and landscape; Wi-Fi share without the password, calendar times, escaped `;`, 48 dp touch targets, slider and emoji labels, viewer crash guard, fixed error texts, Book split failure message. `BarcodeWifiFieldsTest` (2). Not seen on a device. |
| next | F81 closes R9: `DocumentRepository` (1,600 lines) is now a 234-line facade over `DocumentStore` (shared locks, history, saving, PDF rebuild), `DocumentCatalog`, `DocumentTrash`, `DocumentImporter`, `PageEditor`, `PageOrganiser` and `DocumentExporter`. Public API unchanged (79 members compared); no behaviour change intended; not seen on a device. |
| next | F80 closes R29: Book mode One page / Two pages / Two pages right first, dashed spine on the live outline, each capture cut out and split into two pages (`imaging/BookSpread.kt`). `BookSpreadTest` (3). Last of the competitor leftovers R26-R35. |
| next | F79 closes R34: Settings › Scanning › Name pattern with {text} {mode} {date} {time} (`imaging/ScanTitle.kt`), applied in both import paths after the smart title; the typed name still wins. Kind labels stay English by the owner's R11 decision. `ScanTitleTest` (3). |
| `0b45583` | F78: Auto-notify What's New on update and add entry in Settings; Quick Settings "Scan" tile and 2x1 widget (Scan, ID card, Import). |
| `be53e49` | F77: Eliminate blank dark screen after language selection with smooth Crossfade; Tags sheet with chips on the document (`TagsSheet.kt`), Tags filter chip and sheet in Files. |
| `72cbda1` | F76: Rename Organise to Organise PDF with 1-page PDF alert dialog; Files sort by Date created and in either direction; Search keeps recent terms. |
| `c50f0d5` | F75: Eliminate RecentPdfStore StrictMode disk read on main thread; duplicate-aware auto-capture and sharp frame shutter wait (`PageSignature.kt`). |
| `9bc0212` | F74: Camera animation delay eliminated, rename Merge/Split PDF, 1-page split alert; camera page strip thumbnails with amber mark and long-press delete with Undo. |
| `5a6a499` | F73: Camera transition smoothness, Images to PDF visual reorder/crop, Organise pages full preview; Share PDF card with quality estimates (`imaging/PdfShare.kt`, `SharePdfSheet.kt`); Compress PDF target size validation and Result Dialog polish. |
| `a43002f` | F72 (closes R38, R17): Home dashboard search launcher scroll clipping fix; PDF Tools upgrades ("Extract Pages" rename, single-page guard, live size estimates, custom target size); star gold contrast; scrolling dialogs. |
| `ad57dd1` | F71 (closes R30, R37): Camera page strip thumbnails and undo delete; Settings Issue Reporting with Automated Diagnostics (`AppDiagnostics`); dependencies into version catalog. |
| `83243c2` | F69: torch bug fixed (the toggle wrote a flag the flash effect never watched); one top-bar flash button (Off/Auto/On/Torch, amber when lit, remembered between scans); review opens on the finished page with Crop · Filters · Adjust tools (closes R27, R31). Verified on the OnePlus. |
| `83243c2` | F70: device layout pass (OnePlus, Android 15): navigation bar no longer hidden, landscape side rail fits, 150 % font fixes (bottom bar, search pill, hero, stat tiles), launch-icon brackets inside the system's circle; launch no longer feels stuck (the system icon keeps scanning while the app loads; `LaunchIntro` starts at 0.60 s). R42 opened (Home scrolls itself ~14 dp). |
| `c27ad4b` | F68 (closes R26): fast save from the camera. `ReviewMode` setting (Always / If needed / Never, default If needed) in Settings › Scanning; `FastSave` decides from the per-page crop and quality; `CameraScannerScreen.reviewOrSave` saves on Done or opens the review on the first page that needs a look. `FastSaveTest` (4); 342 tests 0 failed; lint 0 errors. |
| `3888dd2` | Instrumented tests for the launch, the in-place language change and ID cards, to run from Android Studio (written, not run). |
| `a502f3b` | F67: ID card mode defaults to Both sides; Done after the second side composes one page (front at the top, back below, white A4 shape, `IdCardComposer`) before the review; Retake there re-shoots both sides. `IdCardComposerTest` (2). 338 tests 0 failed, lint 0 errors. |
| `d97deab` | F66 from the owner's screen recording: launch animation 2.25 s (was 1.6 s), wordmark held, indigo-azure-teal flood; the intro follows the app's theme and `MainActivity` sets the system's per-app night mode so the Android 12+ launch screen matches from the next start; `configChanges="locale\|layoutDirection\|uiMode"` so the language picker's Continue (and theme changes) no longer restart the activity (the black frame). CI heaps capped for the free runner; tests 336/0, lint 0 errors. |
| (this commit) | GitHub Actions budget kit: `AGENTS.md` (push in batches, `[skip ci]` for docs), templates in `.github/budget-kit/`, `android.yml` now runs on main and PRs only, docs-skipped, 45-minute timeout (our runs take 25-31 minutes), artifacts 1 day and only on manual runs. Not run on a real runner. |
| next | R38 remaining items fixed in code (Tools options and picks, organiser discard prompt, shutter during rotation, crop curve mode, lock prompt race, Trash dialog); still not compiled or run. |
| next | R38 rotation fixes (10 files, `rememberSaveable`; Pen strokes saver and discard prompt; PDF viewer search kept and Back closes it). **Not compiled or run** (no Android SDK): run `./gradlew :app:compileDebugKotlin` first. Remaining R38 items listed in `AUDIT_FINDINGS.md`. |
| next | Static review of all screens (3 reviewers, code reading only): R38–R41 in `AUDIT_FINDINGS.md`. `ARTEMIS_SCREEN_TEST_PLAN.md` (`ad0d5f5`): 18 Artemis prompts for a device pass; Artemis could not run in the cloud (no SDK/device). |
| next | F66 from the owner's screen recording: launch animation 2.25 s (was 1.6 s), wordmark held, indigo-azure-teal flood; the intro follows the app's theme and `MainActivity` sets the system's per-app night mode so the Android 12+ launch screen matches from the next start; `configChanges="locale\|layoutDirection\|uiMode"` so the language picker's Continue (and theme changes) no longer restart the activity (the black frame). CI: Gradle 3 GB, Kotlin compiler in-process, test heap 2.5 GB (run 73 OOMed at 1.5 GB); tests 336/0, lint 0 errors, bundle and sideload builds pass locally under those limits. |
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
7. **R23: ML Kit usage metrics: closed (F88).** The owner submitted the Play Data safety form on 2026-11-08. Left: host the
   privacy policy at a public HTTPS URL and keep it consistent with the form.
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
