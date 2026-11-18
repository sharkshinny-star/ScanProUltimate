# ScanPro: Play release checklist

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: source review, CI results and official Google documentation, not devices. **PASS** means shown by code, CI or a
document cited here; nothing is marked PASS because it is expected to work. Gates A–O are defined in this file.

Related: [Compliance audit](PLAYSTORE_COMPLIANCE_AUDIT.md) · [Manual actions](PLAYSTORE_MANUAL_ACTIONS.md) ·
[Runbook](PLAYSTORE_RELEASE_RUNBOOK.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

## Gate A: build and CI

| Check | Status | Evidence |
|---|---|---|
| HEAD compiles (`:app:compileDebugKotlin`) | NOT VERIFIED (pending CI) | Run `44375015645` on `3692fbf` cancelled; PR run `44376563792` on `dda768a` failed at Gradle plugin resolution before compiling; last compile pass run 183 on `3c06b85` |
| Unit tests pass | NOT VERIFIED (pending CI) | Run 183: 965 tests, 1 failed (premise fixed `c162957`) |
| Lint (`:app:lintDebug`) | NOT VERIFIED | Not run since `a8f220c` |
| `bundleRelease` succeeds | NOT VERIFIED | `.github/workflows/android.yml` step |
| Sideload `assembleRelease` succeeds | NOT VERIFIED | same |
| Source audit and Room schema | NOT VERIFIED for HEAD | `scripts/ci/scanpro-quality-audit.py`, `check-room-schema.sh` |

## Gate B: signing and versioning

| Check | Status | Evidence |
|---|---|---|
| Upload key exists (RSA ≥ 2048), stored outside git | FAIL | None available; `.gitignore` covers key files |
| Play App Signing enrolled | NOT VERIFIED | No Console access |
| Upload AAB is signed | FAIL | Without `SCANPRO_KEYSTORE*` the build is unsigned (`app/build.gradle.kts` `signingConfigs`) |
| versionCode 7 greater than any uploaded | NOT VERIFIED | `app/build.gradle.kts` |
| versionName 4.2.0 matches release notes | PASS | `app/build.gradle.kts`, [Store listing §7](PLAYSTORE_STORE_LISTING.md) |
| No secrets committed | PASS | Platform audit `git log --all` search; `.gitignore` |

## Gate C: target SDK and platform

| Check | Status | Evidence |
|---|---|---|
| targetSdk ≥ 36 | PASS | `targetSdk = 37` |
| Android 17 behaviour (large screens, predictive back, edge-to-edge) | NOT VERIFIED | [Compatibility §2](PLAYSTORE_COMPATIBILITY_REPORT.md), R2 |
| FGS type and timeout handling | PASS (code) / NOT VERIFIED (device) | `OperationForegroundService.kt`, `123b048` |

## Gate D: native code and 16 KB

| Check | Status | Evidence |
|---|---|---|
| Committed 64-bit `.so` LOAD alignment ≥ 0x4000 | PASS | Platform audit `readelf` |
| Every `.so` in the AAB aligned | NOT VERIFIED | Release check not run (PS-04) |
| APK zip alignment `-P 16` | NOT VERIFIED for HEAD | Historic pass 2026-11-13 (`BUILD_AND_TEST.md`) |
| `libc++_shared.so` copy chosen by `pickFirsts` | NOT VERIFIED | `app/build.gradle.kts` |

## Gate E: permissions

| Check | Status | Evidence |
|---|---|---|
| Every permission has a purpose and denial handling | PASS (code) | [Permissions §1](PLAYSTORE_PERMISSIONS_AUDIT.md) |
| Merged manifest reviewed | NOT VERIFIED | Release check section 3 not run |
| All files access: declaration filed **or** build without it | FAIL | Not filed; switch exists (`123b048`) but Files cards ungated (PS-11) |
| No restricted permissions beyond the above | PASS (app manifest) | `app/src/main/AndroidManifest.xml` |

## Gate F: privacy policy

| Check | Status | Evidence |
|---|---|---|
| Content matches the app | PASS (mostly) | `docs/privacy-policy/index.html` after `123b048`; drink-water default wording P2 |
| Placeholders filled, draft box removed | FAIL | 9 `class="fill"` |
| Hosted at a public HTTPS URL | FAIL | Not hosted |
| URL in the app (bundle gate) | PASS (gate code) / NOT VERIFIED (run) | `app/build.gradle.kts` `bundleRelease` `doFirst` |
| One contact address everywhere | FAIL | PS-08 |

## Gate G: Play Console declarations

| Check | Status | Evidence |
|---|---|---|
| Data safety | FAIL (draft ready) | [Declarations §1](PLAYSTORE_DECLARATIONS.md) |
| Ads, app access, target audience, content rating | FAIL (drafts ready) | Declarations §2–4, §9 |
| FGS dataSync with video | FAIL | Declarations §5 |
| Financial features, health | FAIL | Declarations §7–8 |
| Trader status | NOT VERIFIED | Owner |
| News, government, account deletion, generative AI | NOT APPLICABLE | Declarations §10 |

## Gate H: security

| Check | Status | Evidence |
|---|---|---|
| Redaction leaves no original text (S1–S4) | NOT VERIFIED (pending CI) | `EditToolsTest`, [Security §2](PLAYSTORE_SECURITY_REPORT.md) |
| FileProvider limited, not exported | PASS | `res/xml/file_paths.xml`, manifest |
| No cleartext traffic | PASS | `usesCleartextTraffic="false"` |
| Release logs stripped (v/d/i) | PASS (rule) / NOT VERIFIED (dex) | `app/proguard-rules.pro:49-53` |
| Claims accurate (Sign = visual, Flatten, "nothing uploaded") | FAIL | PS-07; Flatten translations |

## Gate I: functional testing on devices

| Check | Status | Evidence |
|---|---|---|
| Manual plan run on a signed release build | NOT VERIFIED | [Test report §4](PLAYSTORE_TEST_REPORT.md) |
| Device matrix | NOT VERIFIED | [Compatibility §5](PLAYSTORE_COMPATIBILITY_REPORT.md) |
| Instrumented tests | NOT VERIFIED (never run) | `app/src/androidTest` (8 tests) |
| Offline use | NOT VERIFIED | Test report items 19–20 |

## Gate J: performance and vitals readiness

| Check | Status | Evidence |
|---|---|---|
| Memory and bitmap under Feb 2027 thresholds | NOT VERIFIED | [Test report §5](PLAYSTORE_TEST_REPORT.md) |
| R8 and resource shrinking | PASS (config) | `app/build.gradle.kts` |
| Baseline profile shipped | PASS (file) / NOT VERIFIED (coverage) | `app/src/main/baseline-prof.txt` (47 lines, last changed `6a7ee6b`, 2026-11-12); regenerating with the `baselineprofile` module needs a device |
| DEX optimisation ≥ 25 % | NOT VERIFIED | App bundle explorer after upload |

## Gate K: store listing and assets

| Check | Status | Evidence |
|---|---|---|
| Name ≤ 30, short ≤ 80, full ≤ 4,000 | PASS (28 / 71 / 2,947) | [Store listing](PLAYSTORE_STORE_LISTING.md) |
| Text accurate, no superlatives | PASS (draft) / owner approval pending | same |
| Icon 512, feature graphic, 2–8 screenshots | FAIL | Not made |
| Contact e-mail | FAIL | PS-08 |

## Gate L: accessibility and localisation

| Check | Status | Evidence |
|---|---|---|
| Translations reviewed (hi, ta, te, kn, ml) | FAIL | Unreviewed (PS-14) |
| Translation completeness | NOT VERIFIED for HEAD | Source audit in CI |
| TalkBack, font scale 200 %, contrast | NOT VERIFIED | No device |

## Gate M: account, identity and Console setup

| Check | Status | Evidence |
|---|---|---|
| Developer account identity verified | NOT VERIFIED | Owner |
| App created; `com.scanpro.app` registered | NOT VERIFIED | Owner |
| Sideload build registered / key strategy decided | NOT VERIFIED | Owner |

## Gate N: testing tracks

| Check | Status | Evidence |
|---|---|---|
| Internal test release installed and smoke-tested | NOT VERIFIED | Runbook step 7 |
| Closed test 12 testers × 14 days (if personal account) | NOT VERIFIED | Owner; account type unknown |
| Pre-launch report reviewed | NOT VERIFIED | After the first upload |

## Gate O: rollout and monitoring

| Check | Status | Evidence |
|---|---|---|
| Staged rollout plan | PASS (written) | [Runbook §6](PLAYSTORE_RELEASE_RUNBOOK.md) |
| Vitals thresholds and halt criteria agreed | PASS (written) / owner approval pending | Runbook §7 |
| Rollback plan (halt + fixed versionCode) | PASS (written) | Runbook §8 |
