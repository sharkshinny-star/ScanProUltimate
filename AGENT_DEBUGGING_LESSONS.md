# Debugging lessons for agents

Read this when a bug "has no error" or a fix attempt keeps going in circles. Add a new case at the bottom after each hard bug.

## Case 1: crop editor opened and closed by itself (2026-11-10)

**Symptom.** Tapping Crop (or a page) in the document screen did nothing visible. No crash, no useful logcat lines.

**Cause.** In `CropEditorDialog.kt`, the outline is drawn with `Path().apply { ...; close() }`. A bare `close()` there called the dialog's own `val close = { ... onDismiss() }` lambda, not `Path.close()`. Kotlin gives a local variable with `invoke` priority over a member of the implicit receiver. The first draw therefore dismissed the dialog. Fix: `this.close()`.

**Why earlier attempts failed, and what worked.**

1. **Do not stop at "no crash".** An agent read the code, found nothing wrong, and guessed. A dialog that vanishes without an exception is being dismissed by code, so find the caller.
2. **Prove the symptom first.** Poll the window count right after the tap:
   `adb shell dumpsys window windows | grep -c "Window #.*com.scanpro.app.debug"`.
   It showed 2 windows, then 1 within 0.6 s: the dialog opened and was removed. That ruled out "tap not received".
3. **Put a stack trace on the dismiss path.** Temporarily change the callback to
   `onDismiss = { android.util.Log.w("Dbg", "dismissed", Throwable()); cropOpen = false }`,
   rebuild, install, repeat the tap, then run
   `adb logcat -d | grep -A14 Dbg`.
   The trace showed the call coming from a **draw** lambda (`DrawBackgroundModifier.draw`). Dismissing from drawing code is never intended, and it pointed straight at line 321.
4. **Remove the debug log afterwards** and confirm with `git diff`.
5. **Verify on the real device**, not only by compiling: open the editor, drag a handle, tap Done, look for the "Crop applied" toast and the changed thumbnail.

**Rule of thumb.** Inside `apply`, `with` or `run` blocks, never call a function whose name also exists as a local variable or parameter (`close`, `dismiss`, `onDone`). Write `this.close()`.

## Working practices that saved time

- **Check the build first.** A half-edited file (here `PdfToolsScreen.kt`) breaks the whole build, so the phone keeps running an old app. Run `./gradlew :app:compileDebugKotlin --offline -q` and read the `e:` lines before testing behaviour.
- **One Gradle build at a time.** Two agents building together caused a bogus "FileNotFoundException during IR lowering". Check `ps aux | grep -i gradle` and wait for the other build to finish.
- **Other agents may be editing the same file.** If the editor says the file changed, re-read it, never overwrite from memory. Check `git diff --stat` and the file's modified time before editing.
- **Long Gradle commands.** Run them in the background writing to a log file that ends with `echo "exit=$?"`, then poll the log with short loops. Long blocking waits time out and can kill the run. `setsid` does not exist on macOS.
- **Driving the phone.** `adb devices` for the serial (currently `dc6b0551`). Use `android layout --device=<serial> --flat` to get element centres, then `adb shell input tap X Y` and `adb shell input swipe X1 Y1 X2 Y2 600`. The home layout shifts between launches, so look before tapping.
- **Be honest about what was verified.** Say plainly when a test was not run or a log was unreadable.

## Case 2: a one-page scan was a 16 MB PDF (2026-11-10)

**Symptom.** The "Scan saved" sheet said "1 page · 16 MB". Nothing was broken, so no log, test or crash pointed at it.

**How it was found.** Measure the artefact, not the code. `adb exec-out run-as com.scanpro.app.debug ls -la files/documents/<id>` showed a 3 MB page JPEG but a 15.6 MB `document.pdf`. `grep -a -c DCTDecode` gave 0 and `FlateDecode` 53, so the page pictures were lossless. Android's `PdfDocument` never writes JPEG for a drawn bitmap.

**Fix.** `PdfExporter` keeps `PdfDocument` for layout and the invisible text layer, then PDFBox places each page picture as a JPEG underneath (`embedJpegs`), with a fallback to the old method. 15.6 MB became 1.1 MB.

**Lessons.**
- Compare what the app produces with what a leading app produces (Adobe Scan: a few hundred kB per page).
- Robolectric cannot run `PdfDocument` ("document is closed!"). Put the risky part in a separate function you can test (`embedJpegs`) and verify the whole thing on the device.
- A classifier that fires on one weak word ("manager", "email", any two capitalised words) will fire on a laptop. Require a second, independent signal such as a phone number or email address.
