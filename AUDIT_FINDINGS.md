# Audit findings

Last updated: 2026-10-31, after commit `fb9e745`. Priorities: P0 data loss or build break, P1 crash/security/reliability,
P2 correctness/UX/i18n, P3 cleanup. Add new findings with the next free ID; never reuse an ID.

## Open

| ID | Priority | Category | Location | Problem | Recommendation |
|---|---|---|---|---|---|
| R1 | P1 | Security | `backup/EncryptedDocumentPackage.kt` | `.scanpro` backups contain the original, unredacted photo so pages stay editable. Anyone with the backup and its password sees redacted content. By design so far. | Owner decides: warn in the backup dialog, or add a "flattened backup" option. Document before changing. |
| R2 | P1 | Compat | `app/build.gradle.kts` `targetSdk = 37` | Raised to 37 (Android 17) after a code review against the Android 17 behaviour changes (see F12). Still needs a run on an Android 17 device or emulator before release: camera and editor on a tablet or foldable (orientation and aspect-ratio locks are ignored there), the foreground service during long jobs, notifications and app lock. | Device-test on Android 17, including a large screen. |
| R3 | P2 | Reliability | `core/operations/OperationRunner.kt` | Queued document edits are held in memory; process death loses edits that hadn't started. Imports are durable (WorkManager). | Persist queued edits as WorkManager jobs. |
| R4 | P2 | UX | `work/ImportScanWorker.kt`, `work/CacheCleaner.kt` | After three failed import attempts, photos are kept 30 days but there's no screen to recover them. | Recovery screen: list kept sessions, re-import or discard. |
| R5 | P2 | i18n | `imaging/PdfToolsEngine.kt` progress text, `core/ui/Support.kt`, `core/designsystem/Brand.kt`, `core/ui/FeedbackPromptDialog.kt` | Hard-coded English strings. | Move to resources. |
| R6 | P2 | Security | `backup/ChunkedAead.kt`, `backup/EncryptedDocumentPackage.kt` | Not re-audited this round (12 unit tests pass: tampering, truncation, wrong password, path traversal, iteration bounds). | Independent review of AES-GCM chunking, PBKDF2 (210k iterations) and header parsing. |
| R7 | P1 | Testing | whole app | Nothing verified on a device; no screenshot or Compose UI tests. | Device pass; add UI tests in CI on an emulator. |
| R8 | P3 | Cleanup | `server/` | Unrelated Node "local HTTP server" placeholder. | Remove if unused (ask owner). |
| R9 | P3 | Maintainability | `data/DocumentRepository.kt` (~1,500 lines) | One class does most data work. | Split by use case. |

## Fixed

| ID | Priority | Category | Location | Problem → fix | Commit | Evidence |
|---|---|---|---|---|---|---|
| F1 | P0 | Build | `scanner/OpenCvDocumentAnalyzer.kt` `steadyGuidance` | Merge left smoothing on `String` while the detector returns `Guidance`; app didn't compile. Now uses the enum. | `cb748f2` | Compiles, tests pass |
| F2 | P0 | Data loss | `scanner/CameraScannerScreen.kt`, `work/ImportScanWorker.kt`, `work/CacheCleaner.kt` | Camera sessions, including scans waiting to import, were in `cacheDir` (evictable); failed imports kept 24 h with an in-memory Retry. Now in `filesDir/pro_scanner_session/`, `.pending_import` marker keeps them 30 days, worker retries automatically (3 attempts), legacy cache sessions still cleaned. | `bfe815d` | `CacheCleanerTest` (4 tests) |
| F3 | P0 | Data loss | `work/CleanupWorker.kt` | Purge always used 7 days, ignoring a 30-day setting. Reads `settings.trashRetentionDays`. | `7e543d5` | Compiles; reviewed |
| F4 | P1 | Crash | `feature/viewer/PdfViewerScreen.kt` | LRU recycled evicted bitmaps still on screen ("recycled bitmap" crash). Eviction no longer recycles. | `09bb6b8` | Compiles; not run on device |
| F5 | P1 | Bug | `feature/viewer/PdfViewerScreen.kt` `openRenderer` | Cache not cleared after password unlock changed the renderer, so old pages could show. `evictAll()` on renderer change; pages keyed per renderer. | `09bb6b8` | Compiles |
| F6 | P1 | Reliability | `feature/tools/PdfToolsViewModel.kt` | PDF tools ran in `viewModelScope` without the foreground service; long jobs died in the background. Now `OperationRunner.keepAlive`. | `65d2c5c` | Compiles |
| F7 | P1 | Security | `app/build.gradle.kts` | PDFBox-Android pulled BouncyCastle 1.72 (known CVEs). Constrained to 1.86. | `b3b7613` | Release build + R8 pass |
| F8 | P1 | Compat | `res/xml/data_extraction_rules.xml` | Invalid `../cache` path removed; scanner sessions excluded from device transfer. | `bfe815d` | Lint clean |
| F9 | P2 | Bug | `UiMessage` call sites | After the merge, trailing lambdas bound to the new `onDismiss`, so Undo/Retry/Open did nothing. | `3c37a28` | Compiles |
| F10 | P2 | i18n | ~30 strings, `SplitMode` | Count messages → plurals; split-mode labels → `@StringRes`. | `f4d2483`, `b3b7613` | Lint clean |
| F12 | P2 | Compat | `app/build.gradle.kts`, `baselineprofile/build.gradle.kts` | targetSdk 36 → 37. Reviewed each Android 17 change against the code: no widgets (RemoteViews limit), no reflection on `MessageQueue` or `static final` fields, no `IntentSender` launched from the background (the ML Kit scanner is launched from a visible activity), native libs loaded with `System.loadLibrary` from the read-only install dir, no Contacts queries (only an insert intent), no background audio, no Bluetooth, no SMS, no LAN access, no `setContentCaptureEnabled` (app lock already uses `FLAG_SECURE`), no orientation or aspect-ratio locks. TLS changes (ECH, Certificate Transparency) only affect ML Kit model downloads through Google servers. | `fb9e745` | Compile, lint, tests and release builds (see `BUILD_AND_TEST.md`); not device-tested (R2) |
| F14 | P3 | Cleanup | about 30 Kotlin files, `translate/.../MlKitTranslator.kt`, 3 markdown files | Remaining IDE style hints: fully qualified names → imports, `when` subjects, `use { }` for ML Kit clients, range check in `CompressPdfEngine`, inlined alias variables, inferable type arguments, KDoc links, test `\$` escapes, markdown tables. Skipped with reasons: `primaryClip` property syntax (Kotlin sees it as `val`, compile error), needed `!!` and type arguments, deliberate aliases that avoid shadowing, `BuildConfig` "constant" conditions (vary per variant), British-English and grammar hints in prose. | `fb9e745` | Compiles, 140 tests pass, lint 0 issues |
| F13 | P3 | Tooling | `opencv/lint.xml`, `opencv/build.gradle.kts`, `.idea/scopes/`, `.idea/inspectionProfiles/`, `.idea/dictionaries/project.xml` | Generated OpenCV code filled the IDE inspection report. Lint now ignores the `:opencv` module; the shared IDE profile switches off the reported inspections inside the "OpenCV (generated)" scope; an "App code" scope runs inspections on everything else; the project dictionary holds technical words (OpenCV names, `rels`, `Calibri`, …). OpenCV sources are untouched. | `fb9e745` | Lint run; IDE profile not verifiable without Android Studio |
| F11 | P3 | Cleanup | many | 80 unused resources, unused splash theme, 45 imports, dead functions/params/opt-ins removed; all compiler warnings fixed; dependencies updated. | `f4d2483`, `b3b7613` | 0 warnings |

## Verified sound (no change needed)

| ID | Area | What was checked |
|---|---|---|
| V1 | Redaction | Burned into the rendered image and OCR re-run, so covered text leaves search, copy, PDF text layer and every export. |
| V2 | App lock | `FLAG_SECURE` while locked or unknown, relock timeout, shared intents queued until unlock; notification deep links handled below the lock gate. |
| V3 | Native libs | arm64 and x86_64 `.so` 16 KB aligned (Android 15+ requirement). |
| V4 | Storage | FileProvider not exported; cloud backup off, device transfer only; crash reports stay on device. |
| V5 | Notifications | Local only; never name a document; neutral public version; neutral text while app lock is on; immutable explicit PendingIntents. |

## Deliberately not changed

- OpenCV Java wrappers (generated from upstream by `scripts/opencv`).
- OpenCV 5.0 upgrade (major version, needs native rebuild).
- IDE-only style hints and false positives (argument order, "unused" symbols that are used).
- "Tips" notifications (low value, nag risk).
