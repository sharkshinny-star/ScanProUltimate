---
name: run-scanproultimate
description: Build, install, run, launch, drive, test and screenshot the ScanPro Android app (com.scanpro.app.debug) on a phone connected over adb from this Mac. Use to start the app, tap through screens by label, run a PDF tool end to end, take screenshots or frame bursts, capture logcat crashes, measure cold start, run unit tests or the on-device edge-detection benchmark.
---

# Run ScanPro

ScanPro is an offline Android document scanner (Kotlin, Compose, Hilt, CameraX, OpenCV, PDFBox). It is driven on a
**real phone over adb** from macOS with `.claude/skills/run-scanproultimate/driver.sh`: it builds and installs with
Gradle in the background, launches the app, reads the screen as text with tap targets (`uiautomator`), taps by label,
types, picks files in the system picker, takes screenshots and runs the tests. Paths below are relative to the repo
root. Screenshots and logs land in `/tmp/scanpro-run/`.

## Prerequisites (verified on this Mac)

- Android Studio installed (its JDK is used: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`) and the SDK
  at `~/Library/Android/sdk` (the driver adds `platform-tools` to `PATH`).
- A phone with USB debugging on, plugged in. Check:

```bash
.claude/skills/run-scanproultimate/driver.sh doctor
```

## Build and install (agent path)

Gradle runs detached because a build takes 2 to 10 minutes and tool calls time out. Poll in steps of at most 75 s.

```bash
.claude/skills/run-scanproultimate/driver.sh build-start :app:installDebug :app:installDebugAndroidTest
.claude/skills/run-scanproultimate/driver.sh build-wait 75   # repeat until it prints DONE 0 (or e: ... errors)
```

`build-start` with no arguments installs only the app. `installDebug` updates the app in place: data is kept.

## Drive the app (agent path)

```bash
D=.claude/skills/run-scanproultimate/driver.sh
$D log-start                      # logcat to /tmp/scanpro-run/logcat.txt
$D launch                         # opens MainActivity, prints the focused window
$D tapt Tools 3                   # tap the element whose label is exactly "Tools", then wait 3 s
$D tapt "Search tools" 1.5
$D type "excel"
$D dump                           # every visible label with its centre: "PDF to Excel 377 598"
$D ss tools-search                # -> /tmp/scanpro-run/tools-search.png (look at it)
```

Run a PDF tool end to end with your own test file (never the user's files):

```bash
$D make-test-pdf                  # -> /tmp/scanpro-run/scanpro-table.pdf (a 4x3 table, text layer)
$D push /tmp/scanpro-run/scanpro-table.pdf   # -> /sdcard/Documents/scanpro-test/, indexed for the picker
$D back                           # closes the keyboard
$D tapt "PDF to Excel" 3          # opens the system file picker
$D pick scanpro-table.pdf 3       # Documents > scanpro-test > file
$D tapt Start 6
$D dump | head -5                 # "scanpro-table.xlsx", "XLSX · 2.2 kB", "Pages with text: 1 of 1."
```

Read a tool's output file from the app's private storage:

```bash
adb shell "run-as com.scanpro.app.debug ls cache/exports"
adb exec-out "run-as com.scanpro.app.debug cat cache/exports/scanpro-table.xlsx" > /tmp/scanpro-run/out.xlsx
```

Other commands (`$D help` lists them all):

```bash
$D frames 3 burst                 # 3 screenshots as fast as adb allows (~0.3 s apart): animations
$D log-stop && $D crashes         # "ScanPro crashes: 0, ANRs: 0"
$D cold-start 2                   # TotalTime per cold start (debug build: 1.9-3.7 s seen; first after a run is slower)
$D stop                           # force-stop the app
$D cleanup                        # remove /sdcard/Documents/scanpro-test and local screenshots
```

## Tests

```bash
$D build-start ':app:testDebugUnitTest --tests *PdfToExcelTest*'   # or ':app:testDebugUnitTest' for all (~6 min)
$D build-wait 75
$D unit-results                   # "3 tests, 0 failed, 0 skipped" (totals of the last run only)
$D bench                          # edge-detection benchmark on the phone: 12 scenes, IoU and corner error
```

`bench` needs the test APK: `build-start :app:installDebug :app:installDebugAndroidTest` first.

## Run (human path)

Android Studio: select the `app` run configuration and the phone, press Run. Same app, no automation.

## Gotchas

- **Never run Gradle's `connectedDebugAndroidTest`.** It uninstalls the app afterwards and wipes its data (documents,
  settings). Install the test APK and run `am instrument` (what `bench` does).
- The package is `com.scanpro.app.debug` but the activity class is `com.example.scanpro.MainActivity`. A second
  launcher entry (LeakCanary) makes `resolve-activity` return the chooser, so always start the explicit component.
- **Taps are guarded:** `tap`, `tapt`, `type` and `swipe` refuse unless ScanPro or a system picker or chooser is in
  front, so a stray tap never lands in a chat or a phone call. The notification shade counts as "not ScanPro";
  `launch` collapses it. Drive other apps with plain `adb shell input tap`.
- `uiautomator dump` crashes ("UiAutomationService already registered") if it runs while an instrumentation (`bench`)
  holds UI automation. It shows in logcat as a FATAL EXCEPTION in a shell process, not ScanPro; `crashes` filters it.
- **Privacy:** the system file picker opens on the user's recent files, and the share sheet lists their contacts. Use
  `pick` (it goes straight to `Documents/scanpro-test`), take no screenshot while a picker or share sheet shows the
  user's things, and press `back` out of a share sheet.
- `pick` taps fixed spots for "Show roots" (84,179) and "Documents" (504,526): right for this 1080x2412 OnePlus; on
  another phone `dump` the picker and adjust.
- The photo picker on this phone has no "Browse" route, so a test image cannot be imported without showing the user's
  photos. Test with PDFs (text layer) or a real camera capture instead.
- Password fields use the phone's secure keyboard, which blanks screenshots; read them with `dump`.
- Builds can sit at 0% CPU for minutes while the Kotlin daemon (shared with Android Studio) compiles; `build-wait`
  prints "still running". Builds after a killed run can install a stale APK: `build-start` deletes
  `app/build/intermediates/apks_from_bundle/debug` and `intermediary_bundle/debug` first for that reason.
- `cache/exports` in the app's storage also holds outputs the user made; when cleaning up after a test, delete only
  the files your run created, by name.
- `/tmp` is wiped when the Mac restarts: regenerate test files there (or keep them elsewhere).
- The USB link drops for a few seconds now and then ("adb: no devices/emulators found"); wait and run `doctor` again.
- After a reset of the app's data the first launch shows the language picker, welcome and permission screens, and the
  camera shows a first-use dialog ("Start now") per mode; dismiss them before driving.
- A release build is not covered: it needs signing settings that must never be printed or committed.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `ABORT: foreground is not ScanPro or a picker: ... NotificationShade` | `adb shell cmd statusbar collapse`, then retry (`launch` does this). |
| `ERROR: no element labelled 'X'` | `$D dump` and use the exact label; Compose may split a tile into two nodes with the same text. |
| `build-wait` prints `e: file://...` and `DONE 1` | Kotlin compile error in that file and line; fix it and `build-start` again. |
| `adb: no devices/emulators found` mid-run | USB dropped; wait about 15 s, `$D doctor`. |
| `crashes` counts a FATAL EXCEPTION you can't place | Check its `Process:` line; `uiautomator` and other apps are not ScanPro. |
