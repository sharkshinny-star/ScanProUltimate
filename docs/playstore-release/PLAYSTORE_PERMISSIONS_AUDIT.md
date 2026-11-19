# ScanPro: permissions audit

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: `app/src/main/AndroidManifest.xml`, `app/src/noAllFilesAccess/AndroidManifest.xml`, source review, official
Google documentation. Not devices. The **merged** release manifest has not been read (the Release check workflow that
prints it has not run), so permissions added by libraries are listed as expected, NOT VERIFIED.

Related: [Compliance audit](PLAYSTORE_COMPLIANCE_AUDIT.md) · [Declarations](PLAYSTORE_DECLARATIONS.md) ·
[Privacy](PLAYSTORE_PRIVACY_AUDIT.md) · [Policy changelog §6, §7](PLAYSTORE_POLICY_CHANGELOG.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

Paths are under `app/src/main/java/com/example/scanpro/` unless they start with `app/`.

## 1. Declared by the app

| Permission | Source | Purpose | Runtime flow | Denial handling | Play declaration |
|---|---|---|---|---|---|
| `CAMERA` | Manifest l.9; `uses-feature camera.any required=false` | Document, ID card and QR scanning (CameraX) | Welcome permission page (`ui/WelcomeScreen.kt:120`, `StartupPermissions`), again on the camera screen (`scanner/CameraScannerScreen.kt:266`) | Rationale and a Settings path on the camera screen; import via the photo picker still works | None |
| `INTERNET` | Manifest l.12 | ML Kit Translate model download; also used by the ML Kit SDK for diagnostics (manifest comment says "Only for ML Kit Translate": out of date, see PS-07) | Install-time | n/a | None; disclose in Data safety |
| `USE_BIOMETRIC` | Manifest l.13 | Optional app lock (`BiometricPrompt`, strong biometrics or device credential) | Install-time; prompt when the lock is on | Lock falls back to the device credential | None |
| `FOREGROUND_SERVICE` | Manifest l.15-17 (`tools:ignore="ForegroundServicesPolicy"`) | Keep user-started OCR, compression, merges and exports running in the background | Install-time | n/a | Covered by the FGS declaration |
| `FOREGROUND_SERVICE_DATA_SYNC` | Manifest l.18; service `core.operations.OperationForegroundService` `foregroundServiceType="dataSync"`, not exported | Same | Started only from user actions (`core/operations/OperationRunner.kt`); `startForeground` guarded; `onTimeout` stops it | If Android refuses, work continues without the notification (`123b048`) | **Foreground service declaration** (type dataSync, video). Not filed |
| `POST_NOTIFICATIONS` | Manifest l.19 | Progress of background jobs, finished/failed notices, scan-not-saved, Recycle-bin expiry, drink-water reminders | Welcome page on Android 13+ (`WelcomeScreen.kt:121`); Settings (`feature/settings/SettingsNotificationsPage.kt`) | `core/notifications/AppNotifications.kt` checks the permission and `areNotificationsEnabled()` before posting | None |
| `MANAGE_EXTERNAL_STORAGE` | Manifest l.30-32 (`tools:ignore="ScopedStorage"`); removed by `app/src/noAllFilesAccess/AndroidManifest.xml` when built with `-Pscanpro.allFilesAccess=false` | "Files on this phone": read-only list of PDFs, images, Office and text files in shared storage (`feature/phonepdfs/PhonePdfSource.kt`) | Asked at start (F196, owner's choice 2026-11-18): welcome permission page item (`WelcomeScreen.kt:128`) and a one-time prompt for existing users (`ui/FilesAccessStartDialog.kt`, `ui/ScanProApp.kt:293`); both open the system "All files access" page; also the access cards in Files and Home | "Not now" keeps the app usable; access re-checked on resume; Files shows its access card | **All files access declaration** (Permissions Declaration Form). Not filed |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion="29"`) | Manifest l.33-35, plus `requestLegacyExternalStorage` | Same feature on Android 7–10 | Welcome page on API ≤ 29; Files card (`feature/phonepdfs/PhonePdfsScreen.kt:140-158`) | "Don't ask again" leads to app details | None (policy does not cover `READ_EXTERNAL_STORAGE`) |

Components that need protecting: `MainActivity` is exported (launcher, VIEW/SEND/SEND_MULTIPLE for PDF and images;
incoming URIs limited to `content://` from other authorities); `ScanTileService` is protected by
`BIND_QUICK_SETTINGS_TILE`; `ScanWidgetProvider` builds immutable PendingIntents; `HydrationOffReceiver`,
`OperationForegroundService`, `FileProvider` and the startup provider are not exported.

## 2. Expected from libraries (merged manifest, NOT VERIFIED)

| Permission | Likely source | Notes |
|---|---|---|
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | WorkManager 2.12 | ScanPro does not start a dataSync FGS from boot (forbidden at target 35+) |
| `ACCESS_NETWORK_STATE`, `INTERNET` | ML Kit / Play services transport | |
| `USE_FINGERPRINT` | androidx.biometric 1.1.0 | Deprecated alias; harmless |
| `com.scanpro.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature) | androidx.core | |

Verify with the Release check workflow's section 3 or Android Studio's merged-manifest view before filing declarations.

## 3. `MANAGE_EXTERNAL_STORAGE`: risk analysis

**Policy** ([Use of All files access permission](https://support.google.com/googleplay/android-developer/answer/10467955), quoted on 2026-11-18 in the [changelog §6](PLAYSTORE_POLICY_CHANGELOG.md)):

> "You should only access the All files access permission when your app cannot effectively make use of the more
> privacy-friendly best practices, such as the Storage Access Framework or the MediaStore API."

> "Document management apps: Apps that must locate, access, and edit compatible file types outside of its app-specific
> or shared storage". "App must justify in their Console declaration why the solutions documented in the Access
> app-specific files or Storage Access Framework (a privacy-friendly option) documentation are not sufficient".

> Invalid uses include "Media Files access" and "Any File selection activity where the user manually selects individual files".

The 29 October 2026 announcement "reiterated minimum scope principles" for this permission.

**What ScanPro does with it:** reads only. It lists documents across shared storage (including `Download/` and the storage
root, which `ACTION_OPEN_DOCUMENT_TREE` cannot grant on Android 11+), opens them in the viewer and sends them to the PDF
tools. Tools write new files through the picker or app storage. Nothing is uploaded.

**Risk (our judgement, NOT VERIFIED):** material. Arguments for approval: "document management" is listed; SAF cannot
reach `Download/` or the root as a tree; MediaStore does not return other apps' non-media documents. Arguments against:
the use is read-only (the policy text says "edit"); images are media files (Photo Picker covers them); single-file
opening is "file selection"; asking at first launch, before the user opens Files, weakens a "core functionality" claim;
the feature must be "prominently documented and promoted in the app's description". A rejection blocks the whole release.

### Option A: keep the permission and declare it

1. Fill the Permissions Declaration Form as **Document management** (draft: [Declarations §6](PLAYSTORE_DECLARATIONS.md),
   built from the root `PLAY_ALL_FILES_ACCESS.md`, whose two stale lines must be corrected first: PS-12).
2. Record the demo video on a release build.
3. Keep the "Files on your phone" paragraph in the full description ([Store listing](PLAYSTORE_STORE_LISTING.md)).
4. Consider asking in context only (Files tab) rather than at start; that is what Play prefers. The owner chose "at start"
   on 2026-11-18; changing it is the owner's decision.
5. Allow days for review; have Option B ready.

### Option B: upload a build without it

1. `./gradlew :app:bundleRelease -Pscanpro.allFilesAccess=false -Pscanpro.privacyPolicyUrl=https://…` (`123b048`).
   The release manifest then removes the permission (`tools:node="remove"`); `BuildConfig.ALL_FILES_ACCESS = false`
   hides the welcome item and the start prompt.
2. **Gap (PS-11):** the access cards in `feature/phonepdfs/PhonePdfsScreen.kt` (Files tab l.215, Home l.900) do not check
   the flag; on Android 11+ they would open a settings page for a permission the app no longer declares. Gate them or
   replace them with "Add a folder" (SAF tree) before using this build. That code is owned by another session.
3. Remove the "Files on your phone" paragraph from the listing and the All files access sentences from the privacy policy
   (or say they apply to the sideload build only).
4. `READ_EXTERNAL_STORAGE` (≤ 29) stays; it is outside the All files access policy.

Neither option has been built or run on a device.

## 4. Permissions that must not be added

`ACCESS_LOCAL_NETWORK`, `READ_CONTACTS`/`WRITE_CONTACTS`, location, `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` (the photo picker
is used instead), SMS and call log, `SCHEDULE_EXACT_ALARM`, `QUERY_ALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE`. CI's
`scripts/ci/scanpro-quality-audit.py` fails only on `WRITE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES` and an uncapped
`READ_EXTERNAL_STORAGE`; the rest of this list is kept by review.
