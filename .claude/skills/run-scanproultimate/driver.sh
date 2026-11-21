#!/bin/bash
# ScanPro driver: build, install, launch and drive the app on a phone connected over adb (macOS host).
# Usage: .claude/skills/run-scanproultimate/driver.sh <command> [args]   (run from the repo root)
# See SKILL.md next to this file for the commands and the gotchas.
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
# Android Studio's bundled JDK; the stable app may be absent (only Preview installed), so take whichever exists.
if [ -z "${JAVA_HOME:-}" ]; then
  for app in "/Applications/Android Studio.app" "/Applications/Android Studio Preview.app"; do
    if [ -x "$app/Contents/jbr/Contents/Home/bin/java" ]; then JAVA_HOME="$app/Contents/jbr/Contents/Home"; break; fi
  done
fi
export JAVA_HOME
PKG=com.scanpro.app.debug
ACTIVITY=com.example.scanpro.MainActivity
TEST_PKG=$PKG.test
OUT=/tmp/scanpro-run
DEVICE_DIR=/sdcard/Documents/scanpro-test
mkdir -p "$OUT"

die() { echo "ERROR: $*" >&2; exit 1; }
focus() { adb shell dumpsys window | grep mCurrentFocus | grep -v "=null" | head -1; }

# Taps are refused unless ScanPro or a system picker/chooser is in front: a stray tap must never land in
# another app (a phone call, a chat). Competitor apps are driven with plain `adb shell input tap` on purpose.
guard() {
  case "$(focus)" in
    *scanpro*|*documentsui*|*intentresolver*|*permissioncontroller*|*photopicker*|*PopupWindow*|*"Pop-Up Window"*) return 0;;
  esac
  echo "ABORT: foreground is not ScanPro or a picker: $(focus)" >&2; exit 9
}

# Every visible text / content-description with the centre of its bounds: "<label> <x> <y>".
dump() {
  adb shell uiautomator dump /sdcard/scanpro_ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/scanpro_ui.xml | python3 -I -c '
import sys, re
x = sys.stdin.read()
for m in re.finditer(r"(?:text|content-desc)=\"([^\"]+)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", x):
    print(m.group(1)[:90].replace("&amp;", "&"), (int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2)
'
}

# Centre of the first element whose label is exactly $1.
find_text() { dump | awk -v N="$1" '{ l=$0; sub(/ [0-9]+ [0-9]+$/, "", l); if (l == N) { print $(NF-1), $NF; exit } }'; }

cmd=${1:-help}; shift || true
case "$cmd" in
  doctor)
    adb devices | tail -n +2 | grep -w device >/dev/null || die "no phone over adb (adb devices is empty)"
    adb devices -l | tail -n +2 | head -1
    [ -x "$JAVA_HOME/bin/java" ] || die "JAVA_HOME not found: $JAVA_HOME"
    echo "JAVA_HOME ok"
    adb shell pm list packages | grep -q "^package:$PKG$" && echo "$PKG installed" || echo "$PKG NOT installed (run build-start)"
    ;;

  build-start)   # [gradle tasks...]  default: :app:installDebug. Runs detached; poll with build-wait.
    tasks=${*:-:app:installDebug}
    cd "$ROOT" || exit 1
    # A killed earlier run leaves these behind and the next install then fails or installs a stale APK.
    rm -rf app/build/intermediates/apks_from_bundle/debug app/build/intermediates/intermediary_bundle/debug
    rm -f "$OUT/build.log"
    nohup bash -c "./gradlew $tasks -q > '$OUT/build.log' 2>&1; echo DONE \$? >> '$OUT/build.log'" >/dev/null 2>&1 &
    echo "started: $tasks (log $OUT/build.log)"
    ;;

  build-wait)    # [seconds=100]  waits up to N seconds; prints DONE <code> or "still running"
    limit=${1:-100}; i=0
    while [ $i -lt "$limit" ]; do
      if grep -q "^DONE" "$OUT/build.log" 2>/dev/null; then
        grep -E "^e: |FAILED|Installed on|^DONE" "$OUT/build.log" | head -20; exit 0
      fi
      sleep 5; i=$((i + 5))
    done
    echo "still running ($(wc -c < "$OUT/build.log" 2>/dev/null || echo 0) bytes of log)"
    ;;

  launch)
    adb shell cmd statusbar collapse >/dev/null 2>&1
    adb shell am start -n "$PKG/$ACTIVITY" >/dev/null
    sleep "${1:-4}"; focus
    ;;

  cold-start)    # [runs=3]  prints TotalTime (ms) per cold start
    for _ in $(seq 1 "${1:-3}"); do
      adb shell am force-stop $PKG; sleep 2
      adb shell am start -W -n "$PKG/$ACTIVITY" | grep TotalTime; sleep 3
    done
    ;;

  dump) dump ;;
  focus) focus ;;

  tap)           # x y [wait=2]
    guard; adb shell input tap "$1" "$2"; sleep "${3:-2}" ;;

  tapt)          # "exact label" [wait=2]   tap by text, scrolling down up to 3 times to find it
    label=$1; wait=${2:-2}
    for _ in 1 2 3 4; do
      xy=$(find_text "$label"); [ -n "$xy" ] && break
      guard; adb shell input swipe 540 1700 540 900 400; sleep 1
    done
    [ -n "$xy" ] || die "no element labelled '$label'"
    guard; adb shell input tap $xy; sleep "$wait"; echo "tapped '$label' at $xy"
    ;;

  type) guard; adb shell input text "$(echo "$1" | sed 's/ /%s/g')" ;;
  back) adb shell input keyevent KEYCODE_BACK; sleep "${1:-1.5}" ;;
  swipe) guard; adb shell input swipe "$1" "$2" "$3" "$4" "${5:-400}"; sleep 1 ;;

  ss)            # [name=screen]  -> /tmp/scanpro-run/<name>.png
    f="$OUT/${1:-screen}.png"; adb exec-out screencap -p > "$f"; echo "$f"
    ;;

  frames)        # count prefix  - burst of screenshots as fast as adb allows (about 0.3 s apart)
    for i in $(seq -w 1 "$1"); do adb exec-out screencap -p > "$OUT/$2-$i.png"; done
    ls "$OUT/$2"-*.png
    ;;

  make-test-pdf) # -> /tmp/scanpro-run/scanpro-table.pdf: one page, a 4x3 table in a real text layer
    python3 "$(dirname "$0")/make_test_pdf.py" "$OUT/scanpro-table.pdf" ;;

  push)          # local-file   -> /sdcard/Documents/scanpro-test/, indexed so pickers can find it
    adb shell mkdir -p $DEVICE_DIR
    adb push "$1" "$DEVICE_DIR/" >/dev/null || die "push failed"
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$DEVICE_DIR/$(basename "$1")" >/dev/null
    echo "$DEVICE_DIR/$(basename "$1")"
    ;;

  pick)          # file-name    - in the open system file picker: Documents > scanpro-test > file
    focus | grep -q documentsui || die "no system file picker in front"
    adb shell input tap 84 179; sleep 1.5          # show roots
    adb shell input tap 504 526; sleep 2.5         # Documents
    xy=$(find_text scanpro-test); [ -n "$xy" ] || die "scanpro-test folder not listed (push a file first)"
    adb shell input tap $xy; sleep 2.5
    xy=$(find_text "$1"); [ -n "$xy" ] || die "$1 not listed"
    adb shell input tap $xy; sleep "${2:-5}"; echo "picked $1"
    ;;

  log-start) adb logcat -c; (nohup adb logcat -v time > "$OUT/logcat.txt" 2>&1 &); echo "$OUT/logcat.txt" ;;
  log-stop) pkill -f "adb logcat -v time"; echo "$(wc -l < "$OUT/logcat.txt") lines" ;;
  crashes)       # ScanPro's own crashes and ANRs in the capture (uiautomator and other processes are ignored)
    c=$(grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -c "Process: $PKG")
    a=$(grep -c "ANR in $PKG" "$OUT/logcat.txt")
    echo "ScanPro crashes: $c, ANRs: $a (other processes' crashes ignored)"
    grep -B2 -A14 "Process: $PKG" "$OUT/logcat.txt" | head -40
    ;;

  bench)         # edge-detection benchmark on the phone (needs build-start ':app:installDebug :app:installDebugAndroidTest')
    adb logcat -c
    adb shell am instrument -w -e class com.example.scanpro.scanner.ScanDetectionBenchmarkTest \
      $TEST_PKG/androidx.test.runner.AndroidJUnitRunner | grep -E "^OK|FAIL|AssertionError"
    adb logcat -d -s SCANBENCH | sed -E 's/.*SCANBENCH: //'
    ;;

  unit-results)  # totals of the last :app:testDebugUnitTest run
    python3 - "$ROOT/app/build/test-results/testDebugUnitTest" <<'EOF'
import glob, re, sys
t = f = s = 0
for p in glob.glob(sys.argv[1] + "/*.xml"):
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', open(p, encoding="utf-8").read(600))
    if m: t += int(m[1]); s += int(m[2]); f += int(m[3]) + int(m[4])
print(f"{t} tests, {f} failed, {s} skipped")
EOF
    ;;

  stop) adb shell am force-stop $PKG; echo stopped ;;
  cleanup) adb shell rm -rf $DEVICE_DIR; rm -f "$OUT"/*.png; echo "removed $DEVICE_DIR and local screenshots" ;;

  *) sed -n '2,3p' "$0"
     awk '/^  [a-z-]+\)/ { name=$1; sub(/\)/, "", name); c=""; if (index($0, "#")) c=substr($0, index($0, "#")); printf "  %-13s %s\n", name, c }' "$0" ;;
esac
