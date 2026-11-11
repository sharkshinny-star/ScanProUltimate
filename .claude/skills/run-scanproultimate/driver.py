#!/usr/bin/env python3
"""Drive the ScanPro debug app on a connected Android phone or emulator over adb.

One command per call; the device keeps the app state between calls, so no REPL is needed.
Run from the repo root:  python3 .claude/skills/run-scanproultimate/driver.py <command> [args]

Commands
  status                 device, lock state, focused window, whether the app runs
  install                ./gradlew :app:installDebug (keeps app data; JAVA_HOME = Studio's JBR)
  launch                 start MainActivity (explicit component: debug has a 2nd LeakCanary launcher)
  stop                   force-stop the app
  ss [name]              screenshot -> /tmp/scanpro-shots/<name>.png (prints the path)
  ui [filter]            list on-screen nodes with text / content-desc and their centre x,y
  tap X Y                tap a point (physical pixels)
  tap-text LABEL [n]     tap the n-th (default 1st) node whose text/desc contains LABEL (case-insensitive)
  wait-text LABEL [sec]  wait until LABEL is on screen (default 15 s); exit 1 on timeout
  type TEXT              type into the focused field (spaces handled)
  key NAME               keyevent, e.g. BACK, HOME, ENTER
  swipe X1 Y1 X2 Y2 [ms] swipe / scroll
  make-pdf [pages]       write a synthetic test PDF (/tmp/scanpro-test.pdf, default 3 pages); no real data
  push FILE              copy a file to /sdcard/Download/ (to pick it in a tool's file picker)
  crash                  print the app's recent crash / FATAL lines from logcat

Env: ANDROID_SERIAL picks the device when several are attached.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.scanpro.app.debug"
COMPONENT = f"{PKG}/com.example.scanpro.MainActivity"
SHOTS = "/tmp/scanpro-shots"
SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))
ADB = os.path.join(SDK, "platform-tools", "adb")
if not os.path.exists(ADB):
    ADB = "adb"
STUDIO_JBR = "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))


def adb(*args, check=True, binary=False):
    cmd = [ADB]
    if os.environ.get("ANDROID_SERIAL"):
        cmd += ["-s", os.environ["ANDROID_SERIAL"]]
    out = subprocess.run(cmd + list(args), capture_output=True, check=False)
    if check and out.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed: {out.stderr.decode(errors='replace').strip()}")
    return out.stdout if binary else out.stdout.decode(errors="replace")


def shell(cmd, check=True):
    return adb("shell", cmd, check=check)


def locked():
    trust = shell("dumpsys trust", check=False)
    m = re.search(r"\(current\).*?deviceLocked=(\d)", trust)
    return m is not None and m.group(1) == "1"


def nodes():
    """Every node of the current screen that has a label, as (label, x, y)."""
    shell("uiautomator dump /sdcard/scanpro_ui.xml", check=False)
    raw = adb("exec-out", "cat", "/sdcard/scanpro_ui.xml", check=False)
    if "<hierarchy" not in raw:
        return []
    found = []
    for n in ET.fromstring(raw[raw.index("<hierarchy"):]).iter("node"):
        label = (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
        b = re.findall(r"\d+", n.get("bounds", ""))
        if label and len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            found.append((label, (x1 + x2) // 2, (y1 + y2) // 2))
    return found


def make_pdf(path, pages):
    """A small valid PDF with one line of text per page, built by hand (no libraries)."""
    objs = ["<< /Type /Catalog /Pages 2 0 R >>", None, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    kids = []
    for i in range(pages):
        text = f"BT /F1 36 Tf 72 700 Td (ScanPro driver test page {i + 1}) Tj ET"
        objs.append(f"<< /Length {len(text)} >>\nstream\n{text}\nendstream")
        objs.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents {len(objs)} 0 R "
                    "/Resources << /Font << /F1 3 0 R >> >> >>")
        kids.append(f"{len(objs)} 0 R")
    objs[1] = f"<< /Type /Pages /Kids [{' '.join(kids)}] /Count {pages} >>"
    out, offsets = b"%PDF-1.4\n", []
    for n, body in enumerate(objs, 1):
        offsets.append(len(out))
        out += f"{n} 0 obj\n{body}\nendobj\n".encode()
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    out += "".join(f"{o:010d} 00000 n \n" for o in offsets).encode()
    out += f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    with open(path, "wb") as f:
        f.write(out)


def matches(label):
    """Exact (case-insensitive) matches first, then substring matches: "Tools" must not pick "PDF tools"."""
    want = label.lower()
    hits = [n for n in nodes() if want in n[0].lower()]
    return sorted(hits, key=lambda n: n[0].lower() != want)


def main(argv):
    if not argv or argv[0] in ("-h", "--help", "help"):
        print(__doc__)
        return 0
    cmd, args = argv[0], argv[1:]

    if cmd == "status":
        print("device:", adb("get-serialno", check=False).strip() or "none")
        print("locked:", locked())
        top = re.search(r"topResumedActivity=\S+ \S+ (\S+)", shell("dumpsys activity activities", check=False))
        print("top activity:", top.group(1) if top else "?")
        print("app pid:", shell(f"pidof {PKG}", check=False).strip() or "not running")
    elif cmd == "install":
        env = dict(os.environ)
        if os.path.isdir(STUDIO_JBR):
            env.setdefault("JAVA_HOME", STUDIO_JBR)
        log = "/tmp/scanpro-install.log"
        with open(log, "w") as f:
            rc = subprocess.run(["./gradlew", ":app:installDebug", "-q"], cwd=REPO, env=env, stdout=f, stderr=subprocess.STDOUT).returncode
        print("installDebug exit", rc, "(log:", log + ")")
        return rc
    elif cmd == "launch":
        shell("input keyevent KEYCODE_WAKEUP")
        if locked():
            print("Phone is locked with a PIN/pattern: unlock it by hand, then launch again.")
            return 1
        print(shell(f"am start -W -n {COMPONENT}").strip().splitlines()[-1])
    elif cmd == "stop":
        shell(f"am force-stop {PKG}")
    elif cmd == "ss":
        os.makedirs(SHOTS, exist_ok=True)
        path = os.path.join(SHOTS, (args[0] if args else time.strftime("%H%M%S")) + ".png")
        png = adb("exec-out", "screencap", "-p", binary=True)
        with open(path, "wb") as f:
            f.write(png)
        print(path)
    elif cmd == "ui":
        for label, x, y in nodes():
            if not args or args[0].lower() in label.lower():
                print(f"{x:5d} {y:5d}  {label[:90]!r}")
    elif cmd == "tap":
        shell(f"input tap {int(args[0])} {int(args[1])}")
    elif cmd == "tap-text":
        index = int(args[1]) - 1 if len(args) > 1 else 0
        hits = matches(args[0])
        if len(hits) <= index:
            print(f"not on screen: {args[0]!r}")
            return 1
        label, x, y = hits[index]
        shell(f"input tap {x} {y}")
        print(f"tapped {label[:60]!r} at {x},{y}")
    elif cmd == "wait-text":
        deadline = time.time() + (float(args[1]) if len(args) > 1 else 15)
        while time.time() < deadline:
            if matches(args[0]):
                print("found", repr(args[0]))
                return 0
            time.sleep(0.7)
        print("timed out waiting for", repr(args[0]))
        return 1
    elif cmd == "type":
        shell("input text " + "'" + " ".join(args).replace(" ", "%s").replace("'", "") + "'")
    elif cmd == "key":
        shell(f"input keyevent KEYCODE_{args[0].upper()}")
    elif cmd == "swipe":
        shell("input swipe " + " ".join(args[:4]) + " " + (args[4] if len(args) > 4 else "300"))
    elif cmd == "make-pdf":
        path = "/tmp/scanpro-test.pdf"
        make_pdf(path, int(args[0]) if args else 3)
        print(path)
    elif cmd == "push":
        adb("push", args[0], "/sdcard/Download/")  # adb prints its progress on stderr, not stdout
        print("pushed to /sdcard/Download/" + os.path.basename(args[0]))
        shell(f"am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/{os.path.basename(args[0])}", check=False)
    elif cmd == "crash":
        pid = shell(f"pidof {PKG}", check=False).strip()
        out = adb("logcat", "-d", "-b", "crash", "-t", "200", check=False)
        lines = [l for l in out.splitlines() if PKG in l or "FATAL" in l or (pid and pid in l)]
        print("\n".join(lines[-40:]) or "no crash lines")
    else:
        print("unknown command:", cmd)
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
