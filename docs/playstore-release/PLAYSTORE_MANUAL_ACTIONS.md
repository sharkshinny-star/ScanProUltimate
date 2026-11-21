# ScanPro: actions only the owner can take

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: source review, CI results and official Google documentation, not devices. None of these actions has been done
by an agent; agents have no Play Console access, no signing key and no device.

Related: [Final readiness](PLAYSTORE_FINAL_READINESS.md) · [Runbook](PLAYSTORE_RELEASE_RUNBOOK.md) ·
[Declarations](PLAYSTORE_DECLARATIONS.md) · [Release checklist](PLAYSTORE_RELEASE_CHECKLIST.md)

Roughly in order. Some Console forms (Data safety, content rating, target audience) may be required before even a testing track can be published, so start action 12 early.

1. **Developer account and identity.** Confirm the Play developer account's identity verification is complete and note
   whether it is a *personal* account created after 13 Nov 2023 (then action 11 applies). [Developer verification](https://developer.android.com/developer-verification).
2. **Create the app** in Play Console with package `com.scanpro.app` (this registers the package for developer
   verification). If the sideload APK will be distributed outside Play, register it too and decide its key (action 7).
3. **Trader status (EEA/UK).** Declare trader or non-trader ([Trader status](https://support.google.com/googleplay/android-developer/answer/17594752));
   a trader's address and phone become public from 1 Mar 2027. Legal decision.
4. **Privacy policy.** In `docs/privacy-policy/index.html` fill the 9 highlighted fields (effective date, legal name ×2,
   contact confirmation ×2, hosted URL, children statement matching the target audience, postal address if needed),
   delete the draft box, and check `grep -c 'class="fill"' docs/privacy-policy/index.html` prints 0. Choose **one**
   contact address for the policy, the listing and `core/ui/Support.kt` `SupportLinks.EMAIL` (PS-08). Host it at a public
   HTTPS URL that is not a PDF (for example GitHub Pages; the repository is public). Put the URL in Play Console.
5. **Decided 2026-11-19 (F225): dropped for Play**, the second choice below; the cards are gated and the listing and policy are changed. Was: **Decide All files access** ([Permissions §3](PLAYSTORE_PERMISSIONS_AUDIT.md)):
   - keep it: correct the two stale lines in `PLAY_ALL_FILES_ACCESS.md` (PS-12), file the declaration
     ([Declarations §6](PLAYSTORE_DECLARATIONS.md)), record the video; or
   - drop it for Play: build with `-Pscanpro.allFilesAccess=false`, have the Files access cards gated first (PS-11),
     remove the listing paragraph and the policy sentences on All files access.
6. **Wording decisions:** approve rewording "nothing uploaded" (PS-07); drink-water reminders on by default or not
   (PS-15); approve the store listing text.
7. **Upload key and Play App Signing.** Create an upload key (RSA ≥ 2048) outside the repository; enrol in Play App Signing
   (let Google generate the app signing key, or upload your own if the sideload APK must update Play installs). Back up
   the key and passwords offline. Never commit them or paste them into a chat.
8. **Build the upload bundle** on your Mac (`JAVA_HOME` = Android Studio's bundled JDK, see `AGENTS.md`):
   ```
   export SCANPRO_KEYSTORE=/path/outside/repo/upload.jks
   export SCANPRO_KEYSTORE_PASSWORD=…  SCANPRO_KEY_ALIAS=…  SCANPRO_KEY_PASSWORD=…
   ./gradlew :app:bundleRelease -Pscanpro.allFilesAccess=false -Pscanpro.privacyPolicyUrl=https://<your hosted policy URL>
   #   -Pscanpro.allFilesAccess=false is the Play build (owner decision 2026-11-19, F225); the build stops without it
   ```
   The build fails if the policy URL is missing or not `https://`. It does **not** fail when the key is missing: check the
   output is signed (`jarsigner -verify app/build/outputs/bundle/release/app-release.aab`).
9. **Run the release check.** Get `.github/workflows/release-check.yml` onto the default branch (GitHub dispatches manual
   workflows from there; your call on merging) and run it, or run locally after the build:
   `scripts/ci/release-check.sh app/build/outputs/bundle/release app/build/outputs/apk/release <merged manifest>`.
   All 64-bit `.so` must say PASS. Also run `bundletool build-apks` + `zipalign -c -P 16 4` on the generated APKs and the
   RELRO check from [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes). Read the merged
   manifest's permission list.
10. **Device testing.** Install the signed release build (internal testing track or `bundletool install-apks`) and run
    [Test report §4–5](PLAYSTORE_TEST_REPORT.md) on at least: an Android 17 Pixel, a 4 GB phone on Android 8–10, a tablet
    or foldable. Record results in `BUILD_AND_TEST.md` and close the R* device checks.
11. **Closed testing (personal accounts only):** at least 12 testers opted in continuously for 14 days before applying
    for production ([App testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)).
12. **Console declarations:** Data safety, ads, app access, target audience, content rating, FGS dataSync (with video),
    financial features, health, All files access if kept ([Declarations](PLAYSTORE_DECLARATIONS.md)).
13. **Store listing assets:** 512 px icon, 1024 × 500 feature graphic, 2–8 phone screenshots (and tablet ones) captured
    from the real release build with demo data ([Store listing §5–6](PLAYSTORE_STORE_LISTING.md)).
14. **Translations:** have a native speaker review the Hindi, Tamil, Telugu, Kannada and Malayalam strings, at least the
    permission, privacy and security texts (PS-14); publish the listing in English until then.
15. **versionCode check:** in Console > App bundle explorer, confirm 7 is higher than every uploaded versionCode; raise
    it in `app/build.gradle.kts` if not, and bump it for every later upload.
