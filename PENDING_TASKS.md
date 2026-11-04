# ScanPro Ultimate — Pending Tasks, Errors & Codebase Status

> **Target Audience:** AI Agents & Developers picking up this codebase.  
> **Last Verified:** 2 November 2026  
> **Branch:** `claude/awesome-clarke-ussjo3`  
> **Build Status:** ✅ Debug & Release Compile (`:app:assembleDebug` PASS) | ✅ Unit Tests (233 passed, 24 skipped, 0 failed) | ✅ Android Lint (0 errors, 1 minor warning)

---

## 1. Executive Summary & Critical Context

ScanPro Ultimate is a private, offline-first document scanner and PDF toolchain app for Android (`com.scanpro.app`, version `4.2.0`, `versionCode 7`, `minSdk 24`, `targetSdk 37`, `compileSdk 37`).

### Core Architecture & Privacy Principles
- **Strictly Offline & Zero Tracking:** No third-party analytics, no Firebase tracking/cloud databases, no external network calls, no user accounts. All processing (OpenCV image filtering, ML Kit OCR, PDF tools, on-device translation) executes locally on device.
- **Dynamic Feature Delivery:** Translation and Language ID reside in the on-demand `:translate` dynamic feature module (or bundled inline when `-Pscanpro.sideload=true`).
- **Data Safety:** Scan sessions live in `filesDir/pro_scanner_session/` with automatic retry logic and recovery for interrupted imports. Document edits use an append-only fsynced journal (`filesDir/edit_journal/`).

---

## 2. Active Working Tree / Uncommitted Changes

The working tree contains several improvements ready to be reviewed and committed:

1. **System Bar Immersion (`MainActivity.kt`):**
   - Implemented `hideSystemNavigationBars()` with `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` to prevent unwanted navigation bar flashes.
   - Enhanced splash exit animation with `FastOutSlowInInterpolator`, smooth zoom & fade, and dynamic theme-aware background color on `decorView` to eliminate white flashes on cold start.
2. **Dynamic Time Greetings (`Brand.kt`, `LibraryScreen.kt`):**
   - Added `greetingRes(hour)` supporting Morning, Afternoon, and Evening uppercase subtitles under the `BrandLockup`.
   - Localized across Hindi, Kannada, Malayalam, Tamil, Telugu, and English.
3. **Dynamic Feature Split Fix (`translate/src/main/AndroidManifest.xml`):**
   - **Critical Fix:** Added explicit `<service>` declarations for `MlKitComponentDiscoveryService` carrying base ML Kit component registrars (`CommonComponentRegistrar`, `VisionCommonRegistrar`, `TextRegistrar`, `BarcodeRegistrar`). Prevents `MissingDependencyException` crash when the split APK is installed at runtime.
4. **Agent Skills Integration (`.agents/skills/`):**
   - Configured starter and custom engineering skills: `adaptive`, `android-cli`, `edge-to-edge`, `jetpack-compose-performance`, `navigation-3`, `testing-setup`, `ux-quality-audit`, `ui-design-system`, and `e2e-journey-qa`.
5. **Code Quality & Syntax Modernization:**
   - Switched to Kotlin collection idioms (`associateBy`), `Duration` extensions (`250.milliseconds`), and OpenCV indexed operator access (`hsv[0, 0, bytes]`).

---

## 3. Errors, Warnings & Known Code Issues

### Current Build & Lint Diagnostics

| Severity | File / Location | Description | Current Impact | Recommended Fix |
|:---|:---|:---|:---|:---|
| 🟡 **Warning** | `app/src/main/res/values/scanpro_icon_colors.xml:5` | Lint `UnusedResources`: `R.color.scanpro_brand` appears unused. | Harmless resource bloat (single color value). | Remove resource if unreferenced, or replace raw hex values in vectors/themes with `@color/scanpro_brand`. |
| 🟡 **Notice** | `app/build.gradle.kts` | 49 `UseTomlInstead` warnings suppressed in `app/lint.xml`. | Dependencies are declared inline instead of via version catalog. | Incrementally migrate remaining inline dependencies to `gradle/libs.versions.toml`. |
| 🟡 **Notice** | `translate/build.gradle.kts` | `:translate` references `libs.mlkit.common`, `libs.translate`, `libs.language.id`. | Verified working, but requires testing on physical device with Play Feature Delivery. | Validate dynamic delivery via `bundletool` or internal test track. |

---

## 4. Pending Tasks & Unresolved Backlog

### P1: High Priority (Pre-Release & Device Verification)

- [ ] **Physical Device Validation Run (Highest Priority):**
  - **Camera Hardware:** Verify CameraX live preview, autofocus tap, torch toggle, and multi-page capture memory footprint across low-end (2GB–3GB RAM) and high-end devices.
  - **Android 17 / targetSdk 37 Compatibility:** Validate foreground service execution during heavy PDF merges/OCR, edge-to-edge system insets, and orientation changes on tablets/foldables.
  - **Office to PDF (`OfficeToHtml.kt`):** Test conversion of actual `.docx`, `.xlsx`, and `.mhtml` files on a real Android WebView print engine.
  - **Biometrics & Folder Lock:** Verify `FolderLock` 30-second background re-lock timeout and `FLAG_SECURE` window protection against screen recording.
  - **QR Code Actions:** Test real Wi-Fi network joining (`ACTION_WIFI_ADD_NETWORKS`) and UPI payment deep-linking with installed banking/UPI apps.

- [ ] **Automated E2E / UI Tests:**
  - Build Compose UI test suites for core user journeys (Scan -> Edge Crop -> Filter -> Export PDF) utilizing the newly added `e2e-journey-qa` guidelines.

- [ ] **Release Signing Configuration:**
  - Configure secure release signing credentials via environment variables (`RELEASE_KEYSTORE_PATH`, `RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`) in CI/CD without hardcoding credentials in `app/build.gradle.kts`.

### P2: Medium Priority (UX, i18n & Feature Hardening)

- [ ] **Additional Indian Regional Languages:**
  - The Play Store analysis recommended adding 4 more regional languages:
    - Marathi (`values-mr`)
    - Gujarati (`values-gu`)
    - Bengali (`values-bn`)
    - Punjabi (`values-pa`)
  - Ensure string parity with `check_translation.py`, update `xml/locales_config.xml`, and add to `AppLocale.SUPPORTED`.

- [ ] **Native Speaker Translation Review:**
  - Hindi (`hi`), Tamil (`ta`), Telugu (`te`), Kannada (`kn`), and Malayalam (`ml`) string resources are complete drafts but need review by native speakers for grammatical accuracy and UI length fit.

- [ ] **Crypto & Backup Security Review:**
  - Perform an independent verification of AES-GCM chunking (`ChunkedAead.kt`) and PBKDF2 derivation (210,000 iterations) in `EncryptedDocumentPackage.kt`.

### P3: Low Priority (Code Quality & Refactoring)

- [ ] **Modularize `DocumentRepository.kt`:**
  - `DocumentRepository.kt` currently exceeds ~1,500 lines. Refactor into domain-specific Use Cases (`ImportPagesUseCase`, `ExportDocumentUseCase`, `EditPageUseCase`, `TrashManagementUseCase`).
- [ ] **Clean up Version Catalog:**
  - Move the remaining dependencies in `app/build.gradle.kts` into `gradle/libs.versions.toml`.

---

## 5. Verification Commands for AI Agents

When making changes, always execute these commands sequentially to guarantee stability:

```bash
# 1. Compile Kotlin sources for debug
./gradlew :app:compileDebugKotlin

# 2. Assemble Debug APK (checks manifest merging & resource packaging)
./gradlew :app:assembleDebug

# 3. Run all JVM unit tests (233+ tests)
./gradlew :app:testDebugUnitTest

# 4. Run Android Lint check
./gradlew :app:lintDebug
```

---

## 6. Key References

- [`AGENT_HANDOFF.md`](file:///Users/zoko/AndroidStudioProjects/ScanProUltimate/AGENT_HANDOFF.md) — Comprehensive session changelog, architecture decisions, and historical fix details (F1 through F37).
- [`AUDIT_FINDINGS.md`](file:///Users/zoko/AndroidStudioProjects/ScanProUltimate/AUDIT_FINDINGS.md) — Ranked security, stability, and UX audit findings.
- [`BUILD_AND_TEST.md`](file:///Users/zoko/AndroidStudioProjects/ScanProUltimate/BUILD_AND_TEST.md) — Build toolchain specifications and execution guides.
- [`.agents/skills/`](file:///Users/zoko/AndroidStudioProjects/ScanProUltimate/.agents/skills) — Ready-to-use agent skills for UX audits, testing, navigation, and design system governance.
