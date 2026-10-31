# Build / validation status

> Superseded by `BUILD_AND_TEST.md`, which has the current build commands and results. Kept for history.

Prepared and re-checked on **8 October 2026**.

## Executed successfully in this environment

- Portable Kotlin model/intelligence/title checks compiled with `kotlinc` and executed successfully.
- The executable checks cover invoice classification/extraction, punctuation-form references such as `Invoice No: INV-2026-1042`, grounded unsupported-question behaviour, OCR smart-title behaviour, crop defaults, manual-adjustment clamping, and action-item extraction.
- All Android XML resources parse as well-formed XML, including the final WorkManager/Hilt manifest configuration.
- Static source scan found no TODO/FIXME/`NotImplemented` implementation markers in application/test code.
- Static Kotlin parser pass found no syntax/arity diagnostics attributable to project code. Running plain `kotlinc` over Android sources naturally reports unresolved Android/Jetpack/OpenCV symbols because their Android classpath is unavailable here.
- The custom Hilt `WorkerFactory` configuration is paired with removal of WorkManager's default AndroidX Startup initialiser in the manifest.
- Project source contains **6,354 production Kotlin lines** plus **117 Kotlin test lines** in the current package.

## Dependency/toolchain baseline

The project is configured for the October 2026 Android toolchain:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0 target wrapper version
- Kotlin / Compose compiler plugin 2.4.10
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`
- CameraX 1.6.2
- OpenCV 4.14.0
- Room 2.8.5
- WorkManager 2.12.0
- Navigation Compose 2.10.2
- Hilt 2.60.1 / AndroidX Hilt 1.4.0
- ML Kit bundled OCR 16.0.1 for Latin, Devanagari, Chinese, Japanese and Korean

These coordinates were selected against current official/repository documentation, but **dependency resolution itself was not executable in this container**.

## Not executable in this environment

This container does **not** have:

- Android SDK / platform 37
- Android Build Tools
- Android Studio
- Gradle executable/distribution
- emulator
- physical Android camera hardware

Therefore the following are explicitly **not claimed**:

- successful Gradle dependency resolution
- successful `assembleDebug` / `bundleRelease`
- produced APK/AAB
- CameraX binding on physical hardware
- OpenCV real-device latency/thermal measurements
- OCR accuracy measurements on the mandatory real-world scan set
- accessibility/TalkBack device pass
- release/minification/device instrumentation pass

## Required first external validation

1. Open in a current Android Studio supporting AGP 9.4.
2. Install SDK 37.
3. Configure/use Gradle 9.6.x and JDK 17+.
4. Sync dependencies.
5. Run `testDebugUnitTest`.
6. Run `lintDebug`.
7. Build `assembleDebug`.
8. Run `connectedDebugAndroidTest`.
9. Test scanning on at least one low-, mid- and high-tier Android device.
10. Run the document-quality matrix in `SCAN_QUALITY_TEST_PLAN.md`.
11. Test a minified signed release build before Play upload.

## Known production-hardening items

- Tune edge-detection and blur/light thresholds against a labelled physical-device dataset.
- Add OCR word/element bounding-box persistence for geometrically exact searchable-PDF text selection.
- Upgrade the current normalised/preset markup controls into a fully direct-manipulation annotation canvas if draggable/resizable annotations are a launch requirement.
- Add explicit Room migrations before shipping a schema-changing update to real users.
- Profile large (50–100 page) documents for memory, thermal throttling and battery.
- Validate every dependency/version during Android Studio Gradle sync.
- Add a backend only if you decide to ship accounts/cloud sync/collaboration/server AI.
- Add Play Billing only after product IDs and entitlement policy are finalised.
