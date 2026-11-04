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

Last updated: 2026-11-04, branch `claude/awesome-clarke-ussjo3`, after the full test run and screen-review fixes (F43, `b76720c`). A competitor comparison (CamScanner, Adobe Scan, OKEN, ACE, Microsoft Lens, Genius Scan, Google's scanner) was started in that session.

## 1. Current state

- **Branch:** `claude/awesome-clarke-ussjo3` on `codingking155/ScanProUltimate`. Work is pushed; no pull request is open.
- **Merged into it:** `main`, `claude/adoring-euler-5s8pn0`, `claude/compassionate-turing-m8ylcw`, `claude/sweet-mccarthy-jajb5j`.
  The other branches add nothing beyond this one. Conflicts were resolved by hand, keeping the more advanced side or combining both.
- **Builds (2026-11-04, `b76720c`):** 0 compiler warnings on a clean compile; 275 unit tests, 0 failed; lint 0 issues (`:app`, `:opencv`); bundle and sideload release builds pass; 16 KB alignment checked (see `BUILD_AND_TEST.md`).
- **Not verified:** nothing has run on a device or emulator. Camera, PDF tools, app lock, notifications and backup/restore
  are untested in practice. No instrumented tests beyond `AppSmokeTest`; `ScreenScreenshotTest` (opt-in) renders 12 screens
  in light and dark for review but asserts nothing.
- **Figma review board:** "ScanPro UI review (real app screens)", https://www.figma.com/design/iHjKny6T0cokWhVhglDkTB, the owner's Starter-plan file
  (View seat: Figma MCP reads are capped at about 20 a month; writes such as `html_to_figma` are not). After UI changes,
  re-render and upload a new board (the boards are plain layers from `html_to_figma`, not linked to code).
- **App version:** 4.2.0 (versionCode 7), `applicationId com.scanpro.app`, minSdk 24, targetSdk 37, compileSdk 37.

## 2. Completed work (newest first)

| Commit | What |
|---|---|
| newest | F44: drag-to-reorder on the review thumbnail strip (long-press, lift, swap; accessibility actions); `ScanReviewReorderTest` (3). |
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

1. **Device testing (required before release).** Camera, every PDF tool, app lock, notifications, backup/restore, on at least one
   low-end and one recent phone.
3. **Release signing** has never been validated; builds are unsigned as configured. Never print signing secrets in logs.
6. **R2: targetSdk is now 37** (code-reviewed; launches under Robolectric on API 24–37, not yet on a device). Test on Android 17, including a tablet or foldable.
7. **F38, F39 and F40 compile, pass the 243 unit tests and lint (0 issues) on `07293b0`, but have never run on a device**: check on a device that Back in the editors asks to discard, rotating keeps mark-up, "Save all (n)" saves from any page, Delete page asks, pinch zoom works, a scan finished while editing another document announces itself instead of opening. For F39 also check both screens in landscape, a new scan's title in Hindi, and the sizes in the library. For F40 also check the review's thumbnail strip, mode hints and the locked-mode message, and the dialogs at 200% font. Still open from the UX review (`UX_REVIEW.md`): R17 (drag-to-reorder, contrast measurements).
7a. **R18: UX suggestions** from the screenshot review (document title truncation, "System default", folder on grid cards; the Recycle bin top bar was fixed in F43). Owner decides; see the Figma board.
8. **Crypto not re-audited** this round (`ChunkedAead`, `EncryptedDocumentPackage`); its unit tests pass.

## 4. Next steps

0. Remaining from the owner's Play Store comparison: four more languages (Marathi, Gujarati, Bengali, Punjabi: same brief and `check_translation.py` as F31, then add each to `locales_config.xml` and `AppLocale.SUPPORTED`); P3 done (PowerPoint export dropped at the owner's request).
1. Run on a device or emulator; record results in `BUILD_AND_TEST.md`.
2. Try Office to PDF on a device with a real .docx, .xlsx and a page saved from Chrome (⋮ › Download).
3. **First: launch on an Android 12-or-older device or emulator (F33 hotfix), including picking a language; also F37 (Scan shortcut before choosing a language, reopen from Recents after sharing a PDF, rotate on Android 12+).** Then device-check F32 with real codes: a Wi-Fi QR (Connect on Android 11+, password copy below), a UPI QR (opens UPI apps), an event/ticket QR, a screenshot from the gallery. Then F31: first launch language picker (pick Hindi, Tamil, Telugu; Android 12 and 13+), Settings > App language, the pen (colours, widths, move in Edit mark-up), lock a folder (hidden from All/Recent/search, unlock prompt, re-locks 30 s after leaving), and have native speakers read the translations. Then F30 on the owner's phone: scanner chrome (zoom, torch, mode strip, Auto/count pill), the Adjust page screen (handles, sliders, Save page), the Document tabs, per-page sheet and Export sheet; then F29 (rating sheet after the first scan: stars, emoji, both buttons) and F28 (every screen changed) and F24–F26 on the owner's phone: document screen sheets and pager, opening from Recent, ID card capture crop, Auto Clean on real documents, signature placement; and F24 (three-button navigation, large display size): launch animation, bottom bar, tool sheet, review screen.
3. Device-check F21/F22: tap Stop on an edit (no "interrupted" message after restart), rapid filter taps keep order, HEIC pick message.
3. Device-check the backup warning (F19) on a redacted document and in Settings > Back up all.
4. Device-test "Unsaved scans": force an import failure, check the banner, notification tap, Save again, Share photos, Discard.
5. Device-test the edit journal: queue several edits behind a long OCR, force-stop the app, reopen, check they run and that the interrupted one is reported.
6. Compose UI tests on an emulator in CI; extend `ScreenScreenshotTest` to Tools, PDF viewer and dialogs, and add golden comparison.
7. Device-test targetSdk 37 on Android 17 (R2).

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
