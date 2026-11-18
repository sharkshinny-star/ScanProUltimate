# ScanPro: test report for the Play release

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (HEAD `3692fbf`)
Evidence: test sources, GitHub Actions results and official Android documentation. **No device or emulator test has
been run by anyone in this session.** Every device row below is NOT VERIFIED; no test is recorded as passing unless a CI
run or a logged local run showed it.

Related: [Compatibility (device matrix)](PLAYSTORE_COMPATIBILITY_REPORT.md) · [Fixes](PLAYSTORE_FIXES.md) ·
[Security](PLAYSTORE_SECURITY_REPORT.md) · [Release checklist](PLAYSTORE_RELEASE_CHECKLIST.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

## 1. Automated tests that exist

| Suite | Location | Size (counted 2026-11-18) | How it runs |
|---|---|---|---|
| JVM and Robolectric unit tests | `app/src/test` | 912 `@Test` methods in 159 files (5 files use parameterised runners, so CI counts more cases) | CI `:app:testDebugUnitTest` (`.github/workflows/android.yml`) |
| Instrumented tests | `app/src/androidTest` (`AppSmokeTest`, `LaunchDeviceTest`, `LanguageChangeDeviceTest`, scanner) | 8 `@Test` in 5 files | Needs a device; **never run in this session** |
| Baseline profile generator | `baselineprofile/src` | 3 `@Test` | Needs a device; not run |
| Lint | `:app:lintDebug` | — | CI |
| Source audit | `scripts/ci/scanpro-quality-audit.py` (translations, forbidden permissions) | — | CI |
| Room schema check | `scripts/ci/check-room-schema.sh` | — | CI |
| Release check | `scripts/ci/release-check.sh` via `release-check.yml` | — | Manual workflow; **never run** (not on the default branch: PS-04) |

## 2. CI history relevant to this release

| Run | Commit | Result |
|---|---|---|
| 183 | `3c06b85` | `:app:compileDebugKotlin` passed; `:app:testDebugUnitTest` **965 tests, 1 failed** (`ImageToolsEngineTest.compressImage_withTargetSize_producesSmallerFile`, a wrong test premise fixed in `c162957`), 82 skipped. Lint, bundle and APKs did not run (from `BUILD_AND_TEST.md`) |
| 185 | `c162957` | Failed: unresolved `scale` in `ToolDemos.kt` (fixed `3dcaa66`) |
| 187 / `44371484511` | `e8a31d3` | Failed: `ToolDemos.kt:378` `NAMED_PARAMETER_NOT_FOUND` |
| `44372838000` | `e764f73` | Failed: same compile error (fixed `3860b76`, `d2ba7aa`) |
| `44373310506` … `44374805216` | `8ddf581` … `b6ab8c2` | Cancelled by newer pushes (concurrency) |
| `44375015645` | `3692fbf` (HEAD) | **In progress** when written: result pending CI (coordinator adds it) |

Last green runs on the branch: push `44323159144`, PR `44353049118` (`a8f220c`). Nothing after `a8f220c` has a full green run yet.

## 3. Tests added in this release (all pending CI)

| File | Tests | Covers | Commits |
|---|---|---|---|
| `imaging/EditToolsTest.kt` | 12 | Redaction leaks (every object read back), inherited resources, fail-closed check, rotation, sideways text, case folding, flatten preview, header fit, password strength | `fbfe713`, `1de4db4`, `b6ab8c2` |
| `imaging/PageToolsUpgradeTest.kt` | 13 | Page tools (F210) | `408b238`, `16ba6f0` |
| `imaging/ConvertToolChecksTest.kt` | 20 | Convert and info tools (F211) | `ed002f9`, `9e8d433` |
| `imaging/ImageToolsEngineTest.kt` | 16 in the file (additions in F193) | Image tools (F193); compress premise fixed | `a7c8711`, `c162957` |

## 4. Manual device test plan (release build, signed with the upload key)

Run on the matrix in [Compatibility §5](PLAYSTORE_COMPATIBILITY_REPORT.md). Record device, Android version, build,
result and a screenshot or recording. Status of every row today: **NOT VERIFIED**.

### Install and first run
1. Fresh install from internal testing; welcome pages; permission page asks camera, notifications (13+) and, in the
   default build, All files access; "Not now" on each leaves the app usable.
2. Existing-user upgrade: one-time All files access prompt appears once only.
3. Build with `-Pscanpro.allFilesAccess=false`: no prompt anywhere, Files tab behaviour (PS-11 gap expected until fixed).

### Camera and scanning
4. Document scan: edge detection, capture, crop handles, perspective, filters (Original, Magic Colour, B&W), retake, reorder.
5. Book mode (split spread), ID card (front and back on one page), batch of 20 pages.
6. Camera denied, then granted from Settings; camera in use by another app; rotate the phone during a scan; low light.

### OCR and text
7. Latin OCR offline (airplane mode) on first scan; Devanagari, Chinese, Japanese, Korean (first use downloads through Play services).
8. Search the library by recognised words; correct and copy text; "Make searchable" on a PDF.
9. Translate: first use installs the module and language pack; then works offline.

### PDF tools
10. Merge, split, extract, insert, rotate, organise, remove blank pages, compress (large 100+ page PDF), page numbers,
    header/footer, watermark, N-up, crop pages, greyscale, invert, flatten, fill form, PDF to images/Word/Excel/text,
    images and Office files to PDF, edit text.
11. Redact text and Redact areas: open the output in another viewer; run `qpdf --qdf` / `mutool show` on a computer and
    search for the redacted word; rotated pages; a form PDF.
12. Sign PDF (visual stamp), Protect (open in another reader with the password), Unlock.
13. Leave the app during a long job: notification shows, job finishes, result saved; return to the app.

### QR and UPI
14. Scan QR (URL with preview, Wi-Fi, vCard, UPI), barcodes; history on/off; CSV export without Wi-Fi passwords.
15. Create a UPI QR for your own UPI ID; tap "Pay with a UPI app" on a scanned code: a chooser opens, nothing is paid by ScanPro.

### Security and storage
16. App lock with biometrics and device credential; 30 s relock; locked folders hidden in Library, Tools pickers, Recent PDFs, notifications.
17. Encrypted backup export and restore; wrong password; Recycle bin restore and expiry.
18. All files access granted then revoked in system settings while the app is open.

### Offline and network
19. Airplane mode from install-after-first-launch: scan, Latin OCR, QR, PDF tools work.
20. Optional: network capture (PCAPdroid) during scan, OCR, QR, translate: only Google endpoints, no document content.

### Platform
21. Predictive back gesture on 16/17; edge-to-edge insets with gesture and 3-button navigation.
22. Tablet landscape and split screen; foldable fold/unfold during a scan and during a tool.
23. Widget (all sizes) and Quick Settings tile; per-app language switch (six languages).

## 5. Performance test plan (release build only; debug builds are several times slower)

Thresholds from [Play technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799)
and [Android vitals](https://developer.android.com/topic/performance/vitals), enforced from **February 2027**, 90th percentile over 28 days:

| RAM tier | Foreground | User-perceived services | Background |
|---|---|---|---|
| 4 GB | 2.00 GB | 1.00 GB | 1.00 GB |
| 6 GB | 2.25 GB | 1.25 GB | 1.25 GB |
| 8 GB | 2.25 GB | 1.50 GB | 1.50 GB |
| 12 GB | 3.25 GB | 1.75 GB | 1.75 GB |
| 16 GB | 4.25 GB | 2.00 GB | 2.00 GB |

Bitmap memory: user-perceived services and background **200 MB**, cached **400 MB**. DEX: 25 % optimisation where DEX > 10 MB.
Core vitals: crash 1.09 %, ANR 0.47 % (8 % per phone model), excessive partial wake locks 5 %.

| # | Test | Tool | Pass mark | Status |
|---|---|---|---|---|
| P1 | Anonymous RSS + swap while compressing a 200-page PDF with the app in the background (FGS) | `adb shell dumpsys meminfo com.scanpro.app` | Below the tier's "user-perceived services" limit | NOT VERIFIED |
| P2 | Bitmap memory during a 20-page batch scan and during OCR | Android Studio memory profiler | < 200 MB while in the background | NOT VERIFIED |
| P3 | Cold start | `adb shell am start -W`, Macrobenchmark | Note value; compare with baseline profile | NOT VERIFIED |
| P4 | Frame timing on Library scroll, Organise pages, camera open/close (R55) | `dumpsys gfxinfo com.scanpro.app framestats`, Perfetto | No sustained jank | NOT VERIFIED |
| P5 | ANR risk: large PDF opened from another app; OCR of 50 pages | Strict mode / Perfetto | No main-thread work > 5 s | NOT VERIFIED |
| P6 | DEX size and optimisation | Play Console App bundle explorer after upload | ≥ 25 % if DEX > 10 MB | NOT VERIFIED |
| P7 | Low-memory device (4 GB, Android 8–10) full flow | Manual | No crash, no OOM | NOT VERIFIED |
