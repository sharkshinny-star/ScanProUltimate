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

Last updated: 2026-10-31, branch `claude/awesome-clarke-ussjo3`, after the "Unsaved scans" commit (newest in `git log`).

## 1. Current state

- **Branch:** `claude/awesome-clarke-ussjo3` on `codingking155/ScanProUltimate`. Work is pushed; no pull request is open.
- **Merged into it:** `main`, `claude/adoring-euler-5s8pn0`, `claude/compassionate-turing-m8ylcw`, `claude/sweet-mccarthy-jajb5j`.
  The other branches add nothing beyond this one. Conflicts were resolved by hand, keeping the more advanced side or combining both.
- **Builds:** debug and release compile with 0 warnings; lint reports 0 issues. Unit tests, lint and release builds pass (see `BUILD_AND_TEST.md`).
- **Not verified:** nothing has run on a device or emulator. Camera, PDF tools, app lock, notifications and backup/restore
  are untested in practice. There are no instrumented or screenshot tests beyond `AppSmokeTest`.
- **App version:** 4.2.0 (versionCode 7), `applicationId com.scanpro.app`, minSdk 24, targetSdk 37, compileSdk 37.

## 2. Completed work (newest first)

| Commit | What |
|---|---|
| newest | "Unsaved scans" recovery screen for scans whose import failed (F15, closes R4); Home banner, notification deep link, `KeptScanStoreTest`. |
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
2. **R1: backups hold unredacted originals.** `.scanpro` backups keep the original photo so pages stay editable. Needs a product
   decision: a warning in the backup dialog, or a "flattened backup" option. Security-sensitive: document the change before making it.
3. **Release signing** has never been validated; builds are unsigned as configured. Never print signing secrets in logs.
5. **R3: queued document edits live in memory** in `OperationRunner`; process death loses edits that had not started.
6. **R2: targetSdk is now 37** (code-reviewed, not device-tested). Test on Android 17, including a tablet or foldable.
7. **R5: remaining English literals** in `PdfToolsEngine` progress text, `Support.kt`, `Brand.kt`, `FeedbackPromptDialog.kt`.
8. **Crypto not re-audited** this round (`ChunkedAead`, `EncryptedDocumentPackage`); its unit tests pass.
9. `server/` is an unrelated Node placeholder; remove if not needed (ask the owner first).

## 4. Next steps

1. Run on a device or emulator; record results in `BUILD_AND_TEST.md`.
2. Ask the owner about R1, then implement the chosen option.
3. Device-test "Unsaved scans": force an import failure, check the banner, notification tap, Save again, Share photos, Discard.
4. Persist queued edits (R3), for example as WorkManager jobs like `ImportScanWorker`.
5. Add Compose UI/screenshot tests and run them on an emulator in CI.
6. Device-test targetSdk 37 on Android 17 (R2).

## 5. Rules carried over from the owner's audit brief

- Inspect before changing; never claim a test passed without running it.
- No network services, analytics, accounts, FCM or tracking. Notifications are local only.
- Never silently delete user data; don't weaken security for convenience; don't remove features without a reason.
- Document architectural, destructive-migration, feature-removal and security-sensitive changes before making them.
- Don't expose signing credentials.
- Push only to `claude/awesome-clarke-ussjo3`, even if the session was assigned another branch (see `CLAUDE.md`); don't open a pull request unless asked.

## 6. Session environment notes

These live outside the repo; a new cloud container won't have them.

- Android SDK was installed at `/opt/android-sdk` (platform 37, build tools), license accepted by the owner. `local.properties` (gitignored) points at it.
- Maven Central returned HTTP 429, so `~/.gradle/init.d/central-mirror.gradle` routed it to Google's Maven Central mirror.
  Recreate it if dependency downloads fail with 429; see `BUILD_AND_TEST.md`.
