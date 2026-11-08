# ScanPro Ultimate — Google Play Release Checklist

This checklist is intentionally conservative. Complete it against the Play Console and a real signed release build before publishing.

## 1. Identity and signing

- [ ] Replace `com.example.scanpro` with the final reverse-DNS application ID **before** first production upload.
- [ ] Replace the temporary product name/branding where desired; do not use CamScanner trademarks, screenshots, icons, or copied store text.
- [ ] Create and protect the upload keystore. Prefer Play App Signing for the production signing key.
- [ ] Set production `versionCode` / `versionName` and keep them monotonically increasing.
- [ ] Build an Android App Bundle (`.aab`) from the release variant.
- [ ] Verify R8/minification release build on physical devices.

## 2. SDK and device compatibility

- [ ] Install the Android SDK/API level required by `compileSdk` in `app/build.gradle.kts`.
- [ ] Verify the current Play target-API requirement again immediately before release. The project currently targets API 36.
- [ ] Test minimum supported Android version (minSdk 24).
- [ ] Test Android 12, 13, 14, 15, and 16/API 36 where devices/emulators are available.
- [ ] Test at least one low/mid-range physical phone with 4–6 GB RAM and one recent flagship.
- [ ] Test tablets/foldables and both portrait/landscape where supported.

## 3. Permissions and privacy

Current intended permissions are deliberately narrow. Re-check the merged release manifest.

- [ ] Camera permission is explained contextually and only requested when entering the custom scanner.
- [ ] No `MANAGE_EXTERNAL_STORAGE`, broad media permission, contacts, location, microphone, phone, or SMS permission has been introduced.
- [ ] Gallery import uses Android Photo Picker/SAF instead of broad storage access.
- [ ] File sharing uses `FileProvider`/content URIs with temporary grants.
- [ ] App-private documents are not silently uploaded.
- [ ] Translation model downloads and Google document-scanner fallback behaviour are accurately disclosed in the privacy policy if shipped.
- [x] Data Safety form submitted by the owner 2026-11-08 (F88); re-check when SDKs change. Matches the exact production build and any analytics/crash SDKs you add later.
- [x] Data Safety form (submitted) covers ML Kit's API usage; the privacy policy must say the same once hosted. Covers ML Kit's API usage and performance metrics (see `AUDIT_FINDINGS.md` R13; check Google's current ML Kit data disclosure).
- [ ] Host the final privacy policy (`docs/privacy-policy/index.html`: fill the 8 highlighted fields, delete the yellow box, check `grep -c 'class="fill"'` prints 0) at a public HTTPS URL and link it in Play Console and the app where required.
- [ ] Review encrypted `.scanpro` backup behaviour and warn users that losing the password means losing access to that backup.

### Data safety form: draft answers (2026-11-08, from Google's ML Kit data-disclosure page; re-check that page on the day)

ScanPro itself sends nothing: no analytics, accounts or crash upload (crash reports stay on the phone; the issue report
is an email the user sends). What leaves the device comes from Google's SDKs in the build: ML Kit text recognition
(Latin, Devanagari, Chinese, Japanese, Korean), barcode scanning, language ID and translation (`com.google.mlkit:*`),
and the Play services document scanner and text recognition fallbacks (`com.google.android.gms:play-services-mlkit-*`).
Google says these collect, over HTTPS, for "diagnostics and usage analytics", and do not pass it to third parties:
device information (maker, model, OS version and build, ML hardware accelerators), application information (package
name, version), per-installation identifiers, performance metrics (latency), API configuration (image format and
resolution), input and output sizes, feature version, event type, error codes, and for translation the configured
source and destination languages. Play treats an SDK's collection as the app's.

| Form question | Answer | Why |
|---|---|---|
| Does the app collect or share any required data types? | Yes | ML Kit's collection above. |
| Is all collected data encrypted in transit? | Yes | Google states HTTPS. |
| Do you provide a way for users to request deletion? | No | ML Kit offers none; the app keeps no account or server data. |
| **App info and performance › Diagnostics** | Collected, not shared; required (cannot be turned off); purpose: Analytics | Performance metrics, event types, error codes, API configuration. |
| **App info and performance › Other app performance data** | Collected; same | Input/output sizes, feature version. |
| **Device or other IDs** | Collected, not shared; required; purpose: Analytics | Per-installation identifiers ML Kit lists. |
| **App activity › Other user-generated content** or **Personal info** | Not collected | Scans, text and photos are processed on the device and never sent. The translation request carries only the chosen language pair, not the text. |
| Photos and videos | Not collected | Processed on the device only. |
| Files and docs | Not collected | Same. |
| Location, Contacts, Messages, Audio, Health, Financial | Not collected | Not requested. A business card's "Add to contacts" opens the Contacts app with prefilled fields; nothing is read. |

Decide before submitting: (1) whether to tick "shared" as well as "collected", since Play's sharing rules treat a transfer
to an SDK provider as sharing unless the provider acts as your service provider; Google's own Play guidance for its SDKs
is the reference. (2) Add the same list to the privacy policy (`PRIVACY_POLICY_TEMPLATE.md` already has the ML Kit
paragraph; keep the two consistent). (3) The `INTERNET` permission exists only for translation model downloads; say so in
the "Data collected" explanations if the form asks.

## 4. Scanner quality acceptance

- [ ] A4/Letter pages: straight-on, 15°, 30°, and strong perspective angles.
- [ ] Receipts: narrow, long, curled, faded thermal paper.
- [ ] Handwritten notes and lined notebooks.
- [ ] Whiteboard mode under reflections.
- [ ] ID front/back export using synthetic/test identification only.
- [ ] Book-spread splitting using non-copyright-sensitive test pages.
- [ ] Dark desk, light desk, patterned background, and low-contrast document edges.
- [ ] Hard shadows, uneven lighting, glare, and low-light capture.
- [ ] Motion blur and intentionally out-of-focus frames.
- [ ] 1, 10, 50, and 100-page sessions for memory pressure and crash behaviour.
- [ ] Manual crop remains usable when automatic edge confidence is weak.
- [ ] Auto-capture does not repeatedly fire while the page is moving.

See `SCAN_QUALITY_TEST_PLAN.md` for measurable acceptance criteria.

## 5. OCR, search, and intelligence

- [ ] Latin OCR tested on printed and mixed-case text.
- [ ] Devanagari, Chinese, Japanese, and Korean OCR tested on supported samples.
- [ ] OCR script switching and re-indexing works after edits.
- [ ] Search returns filename, tag/folder, and OCR-text matches as intended.
- [ ] Correct OCR text manually, save it, then confirm search uses the correction.
- [ ] Searchable PDF text is selectable/searchable in at least two external PDF viewers.
- [ ] Offline document insights never invent facts outside recognised OCR text.
- [ ] Translation clearly communicates that a model may need downloading and results may contain errors.

## 6. PDF/document workflows

- [ ] PDF/JPEG/TXT export.
- [ ] A4, Letter, and automatic page sizing.
- [ ] High/Balanced/Small compression presets.
- [ ] Merge, split/extract, reorder, duplicate, rotate, and delete pages.
- [ ] Watermark, text/highlight/redaction overlays, and signatures.
- [ ] Password-protected PDF opens with the correct password and fails with an incorrect one.
- [ ] Existing PDF import handles valid files and rejects malformed/unsupported input gracefully.
- [ ] Android print flow works with an installed print service or Save to PDF.
- [ ] SAF "Save a copy" survives app restarts and permission boundaries.
- [ ] Long-image and ID-copy exports have sane dimensions/file size.

## 7. Library lifecycle and resilience

- [ ] Create/rename folders; move documents between folders.
- [ ] Favourites, tags, sorting, grid/list, multi-select, bulk share/delete.
- [ ] Trash restore, permanent delete, empty trash, and 30-day cleanup.
- [ ] Kill/relaunch during OCR/export and confirm documents remain consistent.
- [ ] Simulate low storage; fail safely without corrupting the original scan.
- [ ] Import malicious/oversized/corrupt inputs and verify validation/errors.
- [ ] Confirm originals remain intact after repeated crop/filter/rotation changes.

## 8. Security

- [ ] Biometric lock tested with success, cancel, lockout, and unavailable biometrics.
- [ ] Confirm app-private files are not exported with world-readable paths.
- [ ] Verify Android backup configuration matches the intended threat model.
- [ ] Run static dependency/license/security review on every release.
- [ ] Do not put backend/API secrets, service-account keys, or private endpoints in the APK.
- [ ] If cloud sync/AI is later added, use authenticated HTTPS backend calls, explicit consent, server-side secret storage, deletion controls, and clear retention rules.

## 9. Performance/accessibility

- [ ] Measure cold start, camera start, capture-to-editor latency, PDF export time, and OCR time on representative devices.
- [ ] Profile CPU, memory, GPU, thermals, and battery during a 50+ page session.
- [ ] Inspect for bitmap/native-Mat leaks and OutOfMemory crashes.
- [ ] 60 Hz interaction remains smooth; verify 90/120 Hz devices where available.
- [ ] TalkBack traversal and labels make core actions understandable.
- [ ] Test font scales 1.0, 1.3, 1.5, and 2.0; no clipped critical controls.
- [ ] Verify touch targets, colour contrast, dark theme, and dynamic colour.

## 10. Automated/release checks

- [ ] `./gradlew test`
- [ ] `./gradlew lint`
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew connectedAndroidTest` on an emulator/device matrix.
- [ ] `./gradlew bundleRelease`
- [ ] Inspect release AAB/APK with APK Analyser for size, permissions, native ABIs, and unexpected SDKs.
- [ ] Run Play Console pre-launch report and address crashes/ANRs/accessibility findings.
- [ ] Review Play Integrity/abuse controls only if future server-backed paid features require them.

## 11. Store listing

- [ ] Original icon, screenshots, feature graphic, title, short description, and full description.
- [ ] Screenshots are produced from the actual shipping app, not CamScanner or mock competitor UI.
- [ ] Avoid claims such as "100% accurate OCR", "unbreakable encryption", or "better than every scanner" unless independently substantiated.
- [ ] Declare ads/subscriptions accurately. This source tree currently contains no ad SDK or Play Billing implementation.
- [ ] Provide support/contact URL and account-deletion flow only if you later introduce accounts.

## 12. Current limitation before store submission

This source package has not been compiled into an APK/AAB in the generation environment because that environment does not contain the Android SDK/Gradle toolchain. Portable Kotlin checks and static resource/source checks are documented in `BUILD_STATUS.md`. A real Gradle build and physical-device validation are mandatory before Play submission.
