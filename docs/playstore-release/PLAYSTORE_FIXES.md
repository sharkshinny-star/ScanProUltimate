# ScanPro: Play release fixes made on 2026-11-18

Date: 2026-11-18 · Branch: `claude/awesome-clarke-ussjo3` (code reviewed at `3692fbf`; later commits are docs only)
Evidence: source review of each commit, GitHub Actions results and official Google documentation, not devices.
"Result" says what was actually run. When the result is "pending CI", the code has not yet been compiled or tested.

Related: [Compliance audit](PLAYSTORE_COMPLIANCE_AUDIT.md) · [Security](PLAYSTORE_SECURITY_REPORT.md) ·
[Test report](PLAYSTORE_TEST_REPORT.md) · [Final readiness](PLAYSTORE_FINAL_READINESS.md)

CI status when this was written: the last green runs on this branch were push run `44323159144` and PR run
`44353049118` (`a8f220c`). Runs on `e8a31d3` and `e764f73` failed to compile; later runs were cancelled by newer
pushes, including push run `44375015645` on `3692fbf` (all commits below). PR run `44376563792` on `dda768a` (docs commit on top)
failed in 32 s before compiling: Gradle could not resolve the plugin `org.jetbrains.kotlin.plugin.compose` 2.4.21 from
Google or Maven Central (probably a repository or network fault; NOT VERIFIED). So no fix below has compiled yet. The
coordinator adds the outcome of the next run.

## Release and platform fixes

| Fix | Commits | Files | Reason | Test | Result |
|---|---|---|---|---|---|
| A1 build break: `drawTextLines` takes `top`/`bottom` (callers already passed them); `DrawScope.scale` import | `3dcaa66`, `3860b76`, `d2ba7aa` | `core/designsystem/ToolDemos.kt` (signature at l.259 now ends `top: Float = 0.24f, bottom: Float = 0.82f`; import l.22) | CI runs on `e8a31d3` and `e764f73` stopped with `NAMED_PARAMETER_NOT_FOUND` at `ToolDemos.kt:378`; run 185 with an unresolved `scale` | Compile in CI | Signature and import confirmed in the source; compile pending CI |
| A2 release gate for the privacy policy link | `123b048` | `app/build.gradle.kts` (`tasks.matching { it.name == "bundleRelease" }` with `doFirst`), `.github/workflows/android.yml` and `release-check.yml` (`-Pscanpro.allowMissingPolicy=true`) | Play requires the policy inside the app; Settings hides the link when `PRIVACY_POLICY_URL` is empty | CI `bundleRelease` with the opt-out; a local build without the property must fail | CI run 219 passed (`07b2e0e`). The failing case has not been run anywhere |
| A7 mitigation: build switch without All files access | `123b048` | `app/src/noAllFilesAccess/AndroidManifest.xml` (`tools:node="remove"`), `app/build.gradle.kts` (`ALL_FILES_ACCESS` BuildConfig field; release manifest source set when `-Pscanpro.allFilesAccess=false`), `ui/WelcomeScreen.kt:128`, `ui/ScanProApp.kt:293` | Fallback if Google declines the declaration | Build with the switch, read the merged manifest, run the app | Not run. **Incomplete**: `feature/phonepdfs/PhonePdfsScreen.kt` access cards are not gated (owned by another session) |
| A9/A10 partial: privacy policy content | `123b048` | `docs/privacy-policy/index.html`, root `PLAY_ALL_FILES_ACCESS.md` | Policy said the app never asks for all-files access and that translation was the only network use | Reading against the code | Content now states All files access, every network use (ML Kit models via Play services, translation module via Play Feature Delivery, ML Kit diagnostics, in-app review) and the notifications. Legal placeholders remain (9) |
| A12 `startForeground` guarded | `123b048` | `core/operations/OperationForegroundService.kt:33-46` (`runCatching { ServiceCompat.startForeground(…) }`, then `stopSelf(startId)` on refusal); `onTimeout` already stopped the service | After Android 15's daily dataSync budget, `startForeground` throws and would crash the app | Device test only (budget needs > 6 h) | Code read; NOT VERIFIED on a device |
| A6 strip verbose/debug/info logs in release | `123b048` | `app/proguard-rules.pro:49-53` (`-assumenosideeffects class android.util.Log { v, d, i }`) | Internal paths in logcat | Release build plus `apkanalyzer`/dex search | Rule present; effect NOT VERIFIED (no release build inspected) |
| Policy research | `123b048` | `docs/playstore-release/PLAYSTORE_POLICY_CHANGELOG.md` | Official requirements with sources | n/a | Document |
| Release check workflow (manual, owner approved) | `cf6b35e` | `.github/workflows/release-check.yml`, `scripts/ci/release-check.sh` | 16 KB alignment of every `.so` in the bundle (`llvm-readelf`), `zipalign -c -P 16` of the sideload APKs, merged manifest permissions and exported components | Run it | **Not run.** GitHub reports the workflow is not on the default branch, so it cannot be dispatched yet (PS-04) |

## Document security fixes (security audit S1–S4, S8, S11)

| Fix | Commits | Files | Reason | Test | Result |
|---|---|---|---|---|---|
| S1/S2/S8 redaction leaks (F212): kept pages cleaned (resources limited to what they draw, inherited resources resolved, `/Thumb`, `/PieceInfo`, `/B` beads, page `/Metadata` removed, widget `/Parent` cut); references into the source scrubbed; catalogue reduced to `/Type` and `/Pages`; `/Info` removed; search includes comments and form fields | `fbfe713`, `68bb6cc`, `1de4db4` | `imaging/PdfCropRedactTools.kt` (`importClean`, `cleanResources`, `scrubReferences`, catalogue loop at l.690-691) | Original pages and text could survive as unreachable objects in a "redacted" file | `EditToolsTest.aRedactedFileHoldsNothingOfTheWordsAnywhere`, `aPageWithInheritedResourcesKeepsItsFontWhenCopied` | CI run 219 passed (`07b2e0e`) |
| S1 fail-closed check | `1de4db4` | `PdfCropRedactTools.kt:830` `verifyRedacted` walks every object reachable from the trailer; fails when a page dictionary is not one of the document's pages or a string/decoded stream holds a term; the caller deletes the output | Proof before the file is kept | `theWrittenFileIsCheckedAndRefusedWhenAWordIsStillThere` | CI run 219 passed (`07b2e0e`). Limit: ASCII terms of 3+ characters only |
| S3 rotation, case folding, ligatures | `fbfe713` | `PdfCropRedactTools.kt` (`glyphBox(p, rotation)`, per-glyph folding, compatibility forms unfolded) | Boxes could miss text on `/Rotate` pages or after `lowercase()` changed lengths | `boxesFollowEveryPageTurn`, `sidewaysTextOnAnUprightPageIsBoxedSideways`, `caseFoldingKeepsOneCharacterPerCharacter`, `boxesCoverDescendersAndLandOnRotatedPagesWhereTheWordIsSeen` | CI run 219 passed (`07b2e0e`); real files on a device NOT VERIFIED |
| S4 tests that can catch leaks | `fbfe713`, `1de4db4` | `app/src/test/java/com/example/scanpro/imaging/EditToolsTest.kt` (12 tests) | Old tests stubbed the renderer and scanned only the page tree | The suite itself | CI run 219 passed (`07b2e0e`) |
| Redacted copy never named after a redacted word | `68bb6cc` | `feature/tools/PdfToolsViewModel.kt` | File name leak | Code read | CI run 219 passed (`07b2e0e`) |
| S7/S8 cache sweep | `9a3efe5` | `work/CacheCleaner.kt:26` (`redact_in_`, `redactb_in_`, `hf_in_`, `nup_in_`, `img_preview_`) | Unredacted inputs could stay in the cache after a kill | Existing `CacheCleanerTest` style | CI run 219 passed (`07b2e0e`) |
| S11 Flatten wording | `1de4db4`, `3692fbf` | `res/values*/strings_tools_edit.xml`, `res/values/strings_tools_new.xml` | "So nobody can change them" claimed too much | String review | `1de4db4` changed one string in all six languages; `3692fbf` changed `tools_desc_flatten` and `hint_tool_flatten` in English only, so the five translations of those two strings still carry the old meaning (to update) |

## Feature work in the same release (compile status from CI)

| Work | Commits | Tests added | Result |
|---|---|---|---|
| F193 image tools rebuilt | `a7c8711`, `1244933`, `c162957` | `ImageToolsEngineTest` additions (16 tests in the file now) | CI run 183 compiled it; 1 test premise failed and was fixed in `c162957`; re-run pending CI |
| F195–F197 demos, All files access at start, widget, motion | `998d5b8`, `0002a51`, `143bedb`, `edea596`, `95de7ba`, `eb4f044`, `c162957`, `3dcaa66`, `3860b76` | none | CI run 219 passed (`07b2e0e`) (R70) |
| F210 page tools | `408b238` (renumbered `16ba6f0`) | `PageToolsUpgradeTest` (13) | CI run 219 passed (`07b2e0e`) |
| F211 convert and info tools | `ed002f9`, `6b14796` (renumbered `9e8d433`) | `ConvertToolChecksTest` (20) | CI run 219 passed (`07b2e0e`) |
| F212 edit tools | `fbfe713`, `68bb6cc`, `1de4db4`, `3692fbf` (renumbered `b6ab8c2`) | `EditToolsTest` (12) | CI run 219 passed (`07b2e0e`) |

Note: commit messages reuse labels (F194/F195/F210) before renumbering; the commit hashes above are authoritative.

## Not fixed (needs the owner or a device)

Privacy policy legal fields and hosting (PS-01), the All files access decision and declaration (PS-02), signing (PS-03),
device testing (PS-05), Console declarations (PS-06), "nothing uploaded" strings (PS-07), support e-mail (PS-08),
versionCode (PS-10), Files tab gating (PS-11), root `PLAY_ALL_FILES_ACCESS.md` stale lines (PS-12). See
[Manual actions](PLAYSTORE_MANUAL_ACTIONS.md).
