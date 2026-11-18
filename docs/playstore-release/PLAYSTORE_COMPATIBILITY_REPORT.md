# ScanPro: Android compatibility report

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: source review, CI results and official Android documentation, not devices. Nothing in this file was seen on a
phone, tablet, foldable or emulator; every device row is **NOT VERIFIED**.

Related: [Policy changelog §1, §4](PLAYSTORE_POLICY_CHANGELOG.md) · [Test report](PLAYSTORE_TEST_REPORT.md) ·
[Compliance audit](PLAYSTORE_COMPLIANCE_AUDIT.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

## 1. Build targets

| Item | Value | Source | Status |
|---|---|---|---|
| `compileSdk` / `targetSdk` | 37 / 37 (Android 17) | `app/build.gradle.kts` | Meets Play's "36 or higher" (PASS). Behaviour at 37 NOT VERIFIED on a device (R2) |
| `minSdk` | 24 (Android 7.0) | same | Android 7–17 supported |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` (`ndk.abiFilters`) | same | 32-bit x86 left out deliberately |
| Shrinking | R8 (`isMinifyEnabled`) + resource shrinking, `proguard-android-optimize.txt` | same | PASS (config) |
| Delivery | AAB with on-demand `:translate` feature module; language splits off (`bundle.language.enableSplit = false`); sideload APKs with `-Pscanpro.sideload=true` | same | PASS (config) |
| Camera | `android.hardware.camera.any` `required="false"` | `AndroidManifest.xml` | Installs on camera-less tablets and Chromebooks; import still works |

## 2. Behaviour changes that touch this code (Android 7 to 17)

| Android (API) | Change | Where in ScanPro | Status |
|---|---|---|---|
| 7–9 (24–28) | `READ_EXTERNAL_STORAGE` runtime permission for shared files | `READ_EXTERNAL_STORAGE maxSdkVersion="29"`, request in `feature/phonepdfs/PhonePdfsScreen.kt:140-158` ("Don't ask again" leads to app details) | Code read; NOT VERIFIED |
| 10 (29) | Scoped storage | `requestLegacyExternalStorage="true"` (read by Android 10 only) | Code read; NOT VERIFIED |
| 11 (30) | All files access is a special app access (`MANAGE_EXTERNAL_STORAGE`); `ACTION_OPEN_DOCUMENT_TREE` cannot pick the storage root or `Download/` | `PhonePdfSource.kt:41-54` (`isExternalStorageManager`, settings intents) | Code read; NOT VERIFIED. Play policy: see [Permissions](PLAYSTORE_PERMISSIONS_AUDIT.md) |
| 12 (31) | Explicit `android:exported`; `PendingIntent` mutability; FGS start from background restricted | All components declare `exported`; all PendingIntents `FLAG_IMMUTABLE` (platform audit grep); `OperationForegroundService.show/stop` wrap `startForegroundService` in `runCatching` | Code read; NOT VERIFIED |
| 13 (33) | `POST_NOTIFICATIONS` runtime permission; per-app language | Welcome page asks (`ui/WelcomeScreen.kt`), Settings re-asks; `AppNotifications` checks permission and `areNotificationsEnabled()`; `android:localeConfig` (en, hi, ta, te, kn, ml) | Code read; NOT VERIFIED |
| 13+ | Photo picker | `PickVisualMedia`/`PickMultipleVisualMedia` in `MainActivity.kt`, `feature/document/DocumentScreen.kt`, `scanner/CameraScannerScreen.kt`, `feature/feedback/FeedbackFormSheet.kt`; no `READ_MEDIA_*` permission | PASS (no media permission needed). Older-device fallback behaviour NOT VERIFIED |
| 14 (34) | FGS type required; Play FGS declaration | `foregroundServiceType="dataSync"`, `FOREGROUND_SERVICE_DATA_SYNC`, type passed to `ServiceCompat.startForeground` on API 29+ | PASS (code). Declaration not filed |
| 15 (35) | Edge-to-edge enforced; dataSync 6 h/24 h timeout; no dataSync FGS from `BOOT_COMPLETED`; 16 KB pages | `enableEdgeToEdge()` in `MainActivity.kt`; `onTimeout` + guarded `startForeground` (`123b048`); FGS started only from user actions (`OperationRunner`) | Code read; insets and timeout NOT VERIFIED on a device |
| 16 (36) | Edge-to-edge opt-out removed; predictive back on (`onBackPressed` not called); orientation/resizability ignored at sw ≥ 600 dp | `android:enableOnBackInvokedCallback="true"`; Compose `BackHandler` (no `onBackPressed()` override found by grep); no `screenOrientation`/`resizeableActivity` in the manifest or code (grep) | Code read; back animations and tablet layouts NOT VERIFIED |
| 17 (37) | No opt-out from large-screen changes; `System.load()` files must be read-only; certificate transparency on; local network permission; `RemoteViews` bitmap memory limit; reflection on `static final` fields and `MessageQueue` blocked | OpenCV is loaded with `OpenCVLoader` from the APK (no `System.load` in app code); no LAN use; widget layouts use drawables (no bitmaps found in `widget/*.kt`); no `getDeclaredField` in app code (grep) | Our reading: not affected. NOT VERIFIED on Android 17 |

`MainActivity` declares `configChanges` for orientation, screen size, layout, locale and ui mode, so rotation and window
resizing do not recreate the activity; Compose must lay out every screen at any size. That is the main large-screen risk:
the camera screen and editors in landscape and split screen have never been seen.

## 3. Tablets, foldables, ChromeOS

- No orientation lock exists, so Android 16/17 rules change nothing in the manifest, but layouts at sw ≥ 600 dp,
  landscape, split screen, freeform windows and fold/unfold transitions are untested.
- Camera preview aspect ratio and the edge overlay on a rotated large screen: NOT VERIFIED.
- Keyboard and mouse (ChromeOS): `configChanges` includes `keyboard|keyboardHidden`; focus order NOT VERIFIED.

## 4. Native code and 16 KB page size

| Check | Evidence | Status |
|---|---|---|
| Committed OpenCV `libopencv_java4.so` and `libc++_shared.so` (arm64-v8a, x86_64): every LOAD segment aligned 0x4000 | `readelf` in the platform audit; built with NDK r29 and `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` (`scripts/opencv/build-opencv-android.sh:48`) | **PASS** (files in git) |
| armeabi-v7a copies aligned 0x1000 | same | Not covered by the 64-bit rule |
| AGP 9.5.1 stores native libraries uncompressed and aligned; no `useLegacyPackaging` | `build.gradle.kts`, grep | PASS (config) |
| Which `libc++_shared.so` `pickFirsts` selects; alignment of third-party `.so` (CameraX, Compose graphics, DataStore, translate) in the AAB | none yet | **NOT VERIFIED**: run the Release check workflow (`.github/workflows/release-check.yml`), not yet runnable from the branch (PS-04) |
| `zipalign -c -P 16` of AAB-derived APKs; RELRO check | Script checks sideload APKs only; no RELRO check | **NOT VERIFIED** (use `bundletool build-apks` and the developer-page RELRO check) |
| Earlier evidence | `BUILD_AND_TEST.md`: an earlier bundle had 24 `.so`, 64-bit ones 16 KB aligned; sideload APKs passed `zipalign -c -P 16 4` (2026-11-13) | Historic; not re-run for HEAD |

## 5. Device test matrix (all NOT VERIFIED)

| # | Device class | Android | What to cover | Status |
|---|---|---|---|---|
| 1 | Phone, arm64, 4 GB RAM (low end) | 8–10 | Camera scan, OCR, large PDF compress, memory | NOT VERIFIED |
| 2 | Phone, arm64 | 11–13 | All files access grant/revoke, notifications permission (13), photo picker | NOT VERIFIED |
| 3 | Phone, arm64 | 14–15 | FGS dataSync with app in background, edge-to-edge insets | NOT VERIFIED |
| 4 | Pixel phone | 16–17 | Predictive back, every screen, translation download, OCR scripts | NOT VERIFIED |
| 5 | Tablet (sw ≥ 600 dp) | 16–17 | Landscape, split screen, camera screen, editors | NOT VERIFIED |
| 6 | Foldable | 16–17 | Fold/unfold during a scan and during a PDF tool | NOT VERIFIED |
| 7 | Phone, armeabi-v7a only | 7–9 | App starts, OpenCV loads, scan works | NOT VERIFIED |
| 8 | x86_64 emulator or ChromeOS | 14+ | App starts, import, PDF tools (no camera) | NOT VERIFIED |
| 9 | 16 KB page-size emulator or device | 15+ | App starts, OpenCV loads (`OpenCvRuntime`), edge detection | NOT VERIFIED |
| 10 | Device without Google Play services | any | OCR/barcode fall back or show a clear message | NOT VERIFIED |

Test steps are in [Test report §3](PLAYSTORE_TEST_REPORT.md).
