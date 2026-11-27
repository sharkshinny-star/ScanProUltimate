# ScanPro: Play Console release runbook

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: official Google documentation (see the [policy changelog](PLAYSTORE_POLICY_CHANGELOG.md)) and the repository,
not devices and not the Play Console (no access). Console menu names change; follow the on-screen wording where it differs.
Rollout percentages and halt thresholds below are recommendations, not Google rules, except the vitals thresholds.

Related: [Manual actions](PLAYSTORE_MANUAL_ACTIONS.md) · [Declarations](PLAYSTORE_DECLARATIONS.md) ·
[Store listing](PLAYSTORE_STORE_LISTING.md) · [Release checklist](PLAYSTORE_RELEASE_CHECKLIST.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

## 1. Before opening the Console

- [Final readiness](PLAYSTORE_FINAL_READINESS.md) blockers B1–B4 closed (policy hosted, All files access decided, upload
  key ready, CI green on the release commit).
- Release commit tagged locally (e.g. `v4.2.0`), with `BUILD_AND_TEST.md` updated.

## 2. Create the app

1. Play Console > **Create app**: name `ScanPro: Scanner & PDF Tools` (or `ScanPro`), default language English (United
   Kingdom or as chosen), App, Free, accept the declarations.
2. Confirm on Console Home that `com.scanpro.app` is registered (developer verification) once the first bundle is uploaded.
3. Complete account-level items if prompted: trader status, contact details.

## 3. App content (Policy and programmes > App content)

Fill from [Declarations](PLAYSTORE_DECLARATIONS.md): privacy policy URL; ads (no); app access (no special access); content
rating questionnaire; target audience; Data safety; financial features; health; government/news (no); foreground service
(dataSync, with the video link); All files access (only if the default build is uploaded). Save each; the dashboard shows
what is still missing.

## 4. Store presence

Main store listing: name, short and full description, icon, feature graphic, screenshots, category, contact e-mail, from
[Store listing](PLAYSTORE_STORE_LISTING.md). Store settings: category Productivity (owner's choice), tags.

## 5. Signing, build, internal testing

1. **Setup > App signing**: choose Play App Signing (Google-generated key, or upload your own if the sideload APK must
   update Play installs). Register the upload key certificate.
2. Build the signed bundle ([Manual actions 8](PLAYSTORE_MANUAL_ACTIONS.md)) with `-Pscanpro.privacyPolicyUrl=https://…`
   (and `-Pscanpro.allFilesAccess=false` if chosen). Run the release check (action 9).
3. **Testing > Internal testing > Create release**: upload `app-release.aab`, release name `4.2.0 (7)`, release notes from
   [Store listing §7](PLAYSTORE_STORE_LISTING.md). Add testers (e-mail list), roll out.
4. Install from the internal-testing link on the test devices; run [Test report §4](PLAYSTORE_TEST_REPORT.md). Check that
   the translation module installs on demand (it only does so for Play-installed builds).
5. Read the **Pre-launch report** (crashes, accessibility, security warnings, screenshots) and **App bundle explorer**
   (download size, DEX optimisation, 16 KB, per-device APKs). Fix and re-upload with a higher versionCode if needed.

## 6. Closed testing and production

1. Personal account created after 13 Nov 2023: **Testing > Closed testing**, at least 12 testers opted in for 14
   continuous days, then **Apply for production** from the Dashboard and answer Google's questions about the test.
2. **Production > Create release**: promote the tested bundle (same versionCode) or upload a new one.
3. Countries: choose the target countries (all, or start with India and the UK, for example; owner's choice).
4. **Staged rollout** (recommended): 5 % → 20 % → 50 % → 100 %, at least 24–48 h between steps, moving on only if §7 is clean.
5. **Send for review.** Declarations for All files access and FGS may lengthen review by days.

## 7. Monitoring after launch (Android vitals)

Bad-behaviour thresholds ([Android vitals](https://developer.android.com/topic/performance/vitals)): user-perceived crash
rate **1.09 %** overall and **8 %** per phone model; user-perceived ANR rate **0.47 %** and **8 %** per model; excessive
partial wake locks **5 %**. From **February 2027**: memory (anonymous RSS + swap, 90th percentile, per RAM tier) and
bitmap memory (200 MB user-perceived services and background, 400 MB cached) thresholds
([Technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799)).

Recommended halt criteria during a staged rollout (owner may change): crash rate above 1 % or ANR rate above 0.4 % for
the new version; any new crash cluster in camera start, OCR, OpenCV load (`UnsatisfiedLinkError`), the foreground
service, or redaction; one-star reviews mentioning lost documents or redaction; a policy e-mail from Google.

Daily for the first week: Vitals overview, Crashes and ANRs (filter by version 7), Ratings and reviews, Policy status,
Inbox. Weekly after that.

## 8. Rollback

Play cannot roll a version back. **Halt rollout** in Production stops new installs of the bad version. Fix, raise the
versionCode, upload, and resume the staged rollout from 5 %. If a policy rejection arrives (for example All files access),
reply or appeal in the Console, or upload the build without the permission ([Permissions §3](PLAYSTORE_PERMISSIONS_AUDIT.md)) as a new versionCode.

## 9. After release

Update `AGENT_HANDOFF.md`, `BUILD_AND_TEST.md` (what was tested, on which devices) and `AUDIT_FINDINGS.md` (close the
device checks that were done), and the [Final readiness](PLAYSTORE_FINAL_READINESS.md) status. Note the new versionCode.
