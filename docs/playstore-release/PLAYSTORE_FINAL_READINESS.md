# ScanPro: final Play release readiness

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only) · `com.scanpro.app` 4.2.0 (versionCode 7)
Evidence: source review, GitHub Actions results and official Google documentation, not devices. No phone, emulator,
Play Console, signing key or hosted privacy policy was available to any agent in this session.

## Status: **NOT READY**

It cannot be READY or CONDITIONALLY READY today because P0 items are open that code alone cannot close: the privacy
policy is not filled in or hosted, the All files access decision and declaration are open, there is no upload key, the
Console declarations are not filed, no release build has been tested on a device, and CI for HEAD and the release check
have not completed.

**Next status, CONDITIONALLY READY, when all of these hold:** CI green on the release commit (compile, unit tests, lint,
bundle); the release check passes (every 64-bit `.so` aligned, APKs zip-aligned, merged manifest reviewed); a signed
release build passes the device smoke test on at least an Android 17 phone and one older phone; the privacy policy is
hosted; the All files access route is chosen and, if dropping it, the Files cards are gated. What would then remain is
Console filing, closed testing and review.

## Blockers (P0)

| # | Blocker | Action | Who | Detail |
|---|---|---|---|---|
| B1 | Privacy policy has 9 placeholders and a draft box; not hosted; no URL for the bundle | Fill, host at a public HTTPS URL, build with `-Pscanpro.privacyPolicyUrl=` | Owner | PS-01, [Manual actions 4](PLAYSTORE_MANUAL_ACTIONS.md) |
| B2 | **Closed by the owner's decision 2026-11-19 (F225): the Play bundle is built with `-Pscanpro.allFilesAccess=false` and the build enforces it.** Was: `MANAGE_EXTERNAL_STORAGE` asked at start; declaration not filed; high rejection risk | Was: choose: file the declaration (and fix `PLAY_ALL_FILES_ACCESS.md` stale lines), or build with `-Pscanpro.allFilesAccess=false` after gating the Files access cards | Owner (+ the session owning `feature/phonepdfs`) | PS-02, PS-11, PS-12, [Permissions §3](PLAYSTORE_PERMISSIONS_AUDIT.md) |
| B3 | No upload key; Play App Signing not set up; unsigned builds still succeed | Create key, enrol, build signed, verify signature | Owner | PS-03, Manual actions 7–8 |
| B4 | CI run 219 on `07b2e0e` is green (compile, unit tests, lint, unsigned test bundle, sideload APKs); commits after it pending CI. Still open: the release-check workflow (16 KB check of the packaged bundle) has never run and cannot be dispatched until it is on the default branch; no device test; no signed build | Owner: put the workflow on the default branch or approve folding the check into CI; device tests | Coordinator / owner | PS-04, PS-09, [Fixes](PLAYSTORE_FIXES.md) |
| B5 | No device test of any release build (camera, OCR, PDF tools, redaction, FGS, All files access, Android 17 large screens) | Run [Test report §4–5](PLAYSTORE_TEST_REPORT.md) on the [matrix](PLAYSTORE_COMPATIBILITY_REPORT.md) | Owner or an agent with a device | PS-05; R2, R7, R44, R55, R60, R64, R69, R70, R71 |
| B6 | Console declarations not filed (Data safety, FGS dataSync with video, financial, health, content rating, target audience, ads, app access, trader status) | File from the drafts | Owner | PS-06, [Declarations](PLAYSTORE_DECLARATIONS.md) |
| B7 | Store assets missing (icon 512, feature graphic, screenshots); contact e-mail undecided | Capture from the release build; pick one address | Owner | [Store listing](PLAYSTORE_STORE_LISTING.md), PS-08 |
| B8 | Closed test 12 × 14 days if the account is personal and new | Run the closed test | Owner | Policy changelog §11 |

## Required before release (P1)

| # | Item | Action | Detail |
|---|---|---|---|
| R-a | "Nothing uploaded" strings contradict ML Kit diagnostics | Reword in six languages | PS-07 |
| R-b | Support address differs between policy and app | One address everywhere | PS-08 |
| R-c | versionCode 7 vs anything already uploaded | Check in Console | PS-10 |
| R-d | Redaction fixes unproven until `EditToolsTest` passes; non-ASCII terms rely on rasterising | CI, then a device check with `qpdf`/`mutool` | PS-13, [Security §2](PLAYSTORE_SECURITY_REPORT.md) |
| R-e | Translations unreviewed; Flatten wording not updated in five languages | Native-speaker review | PS-14, [Fixes](PLAYSTORE_FIXES.md) |

## What is done (code, not yet proven by CI or a device)

Build break fixed (A1); `bundleRelease` refuses an upload build without an `https://` policy URL (A2); build switch
without All files access (A7, partial); policy content corrected (A9/A10, legal fields open); guarded `startForeground`
(A12); release log stripping (A6); redaction leak fixes with a fail-closed check and tests (S1–S4, S8); cache sweep (S7);
Flatten wording (S11, English); manual release-check workflow (not run). Commits in [Fixes](PLAYSTORE_FIXES.md).

## Documents

[Compliance audit](PLAYSTORE_COMPLIANCE_AUDIT.md) · [Policy changelog](PLAYSTORE_POLICY_CHANGELOG.md) ·
[Fixes](PLAYSTORE_FIXES.md) · [Compatibility](PLAYSTORE_COMPATIBILITY_REPORT.md) · [Privacy](PLAYSTORE_PRIVACY_AUDIT.md) ·
[Permissions](PLAYSTORE_PERMISSIONS_AUDIT.md) · [Security](PLAYSTORE_SECURITY_REPORT.md) · [Tests](PLAYSTORE_TEST_REPORT.md) ·
[Store listing](PLAYSTORE_STORE_LISTING.md) · [Declarations](PLAYSTORE_DECLARATIONS.md) ·
[Release checklist](PLAYSTORE_RELEASE_CHECKLIST.md) · [Manual actions](PLAYSTORE_MANUAL_ACTIONS.md) ·
[Release runbook](PLAYSTORE_RELEASE_RUNBOOK.md)
