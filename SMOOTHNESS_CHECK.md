# Smoothness check on a phone (release build)

Status: **not done.** Frames cannot be measured in the cloud container (see `AGENTS.md`). The fixes F165 and F166 are
unverified until someone runs this on a phone. Judge on a **release** build: a debug build of a Compose app is several
times slower. Package names: release `com.scanpro.app`, debug `com.scanpro.app.debug`.

## Get a release build onto the phone
Release builds are unsigned unless the signing variables are set (see `app/build.gradle.kts`). Either set
`SCANPRO_KEYSTORE`, `SCANPRO_KEYSTORE_PASSWORD`, `SCANPRO_KEY_ALIAS` and `SCANPRO_KEY_PASSWORD` locally (never commit
them), or sign the universal APK with a throw-away key for this test:

```bash
./gradlew -Pscanpro.sideload=true :app:assembleRelease
```

```bash
adb install -r app/build/outputs/apk/release/app-universal-release.apk
```

(If the file is unsigned, sign it first with `apksigner sign --ks <keystore> <apk>`; Android refuses unsigned installs.)

## Count slow frames
Run before and after each scenario. Slow frames are the "Janky frames" line.

```bash
adb shell dumpsys gfxinfo com.scanpro.app reset
```

```bash
adb shell dumpsys gfxinfo com.scanpro.app | grep -E "Total frames|Janky frames|50th|90th|95th|99th"
```

Record the 90th and 99th percentile frame times and the janky percentage. Under 16.6 ms is smooth on a 60 Hz screen
(8.3 ms on 120 Hz).

For a stall you can see but the numbers do not explain, record a 15 second trace and open it at ui.perfetto.dev:

```bash
adb shell perfetto -o /data/misc/perfetto-traces/scan.pftrace -t 15s sched freq gfx view input am wm
```

```bash
adb pull /data/misc/perfetto-traces/scan.pftrace
```

## Scenarios
| # | Scenario | What to watch | Janky % | 99th ms | Notes |
|---|---|---|---|---|---|
| 1 | Home > Start scanning, five times (cold, then warm) | black gap, freeze before the preview (F165) | | | |
| 2 | Close the camera with X, five times | shrinking card, dropped frames | | | |
| 3 | Close with the Back button, five times | same | | | |
| 4 | Close with the back gesture, five times | same (F165 uses the app's slide) | | | |
| 5 | Camera hint pill with a dim page | flicker between Too dark, Hold steady, Ready | | | |
| 6 | Merge 10 large PDFs, watching the progress dialog | gear and ring animation, percentage updates (F165, F166) | | | |
| 7 | Compress a 50 MB PDF, watching the dialog | same | | | |
| 8 | Organise a 100+ page PDF: fling-scroll the grid | blank thumbnails, hitching (F166) | | | |
| 9 | Organise: open a page preview and close it | grid thumbnails re-render | | | |
| 10 | Files with 100+ documents: fling-scroll | thumbnail pop-in, hitching (F166) | | | |
| 11 | Files on this phone with 500+ files | list build time, thumbnails | | | |
| 12 | PDF viewer, 100 pages: fling, then pinch zoom | blank pages, hitching | | | |
| 13 | Crop editor: drag a corner and an edge | lag while dragging | | | |

## If a tool still stutters
Write down the tool, the exact step, the janky % and 99th percentile, the device, and whether it is release or debug.
Then capture the Perfetto trace for that step. Do not guess a fix without it. Compare against a debug run only to learn
how much of the stutter is the debug build.
