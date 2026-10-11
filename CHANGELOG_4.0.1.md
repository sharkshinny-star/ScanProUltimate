# ScanPro 4.0.1 source bundle

This is an incremental source update of the uploaded ScanPro Ultimate 4.0 project, not a validated release build or an entirely completed 4.1 transformation.

## Included in this project

- Fixes invalid `SimpleDateFormat` patterns for newly scanned/imported documents (`Illegal pattern character 'n'`).
- ID-card mode with guided **Front only** and **Front + back** capture. A two-sided session requires two captures before Save; Undo last allows retakes. ID scans cap at the selected page count.
- Automatically opens the newly saved camera scan's document detail screen so PDF and JPG actions are immediately accessible.
- Prominent **Share PDF** and **Share JPG** actions on the document detail screen. JPG shares work for one or multiple pages; missing files produce informative messages.
- Makes Scan / Photos / PDF import buttons scroll horizontally on narrow devices instead of clipping.
- New green ScanPro identity included as Play Store artwork (`scanpro-store-icon.png`) with adaptive, monochrome/themed and legacy launcher assets.
- No database schema or app ID changes. User document data/migrations are not intentionally changed.

## Build / test limitations

This bundle has **not** been built into an APK/AAB. The current environment lacks Android SDK/platforms, Gradle executable/wrapper JAR, and Android emulator. Only available static resource and portable date pattern checks are run here. A production release needs Gradle sync, Android unit/instrumentation tests, physical camera tests, accessibility/landscape checks, and redaction/security review. The existing project already has additional functionality and known quality risks documented in `BUILD_STATUS.md`.

## Build steps

Open `project` in Android Studio with the Android SDK required by the existing `app/build.gradle.kts`, install/configure a compatible Gradle distribution, sync dependencies, then run `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and physical-device scans before release. The ZIP includes the original Gradle wrapper properties but **does not** contain a Gradle wrapper JAR or script.
