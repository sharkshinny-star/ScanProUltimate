#!/usr/bin/env python3
"""Read-only source checks for ScanPro, run locally and in Android CI before the Gradle build.

Checks what lint does not:
  * every string resource file (default and the five Indian-language sets) parses;
  * every translatable default string exists in each language, with the same printf placeholders
    (a missing or mismatched %1$d crashes at runtime or shows the wrong value);
  * the manifest asks for no broad storage or package-query permission (ScanPro is offline-first and
    works through the system file picker);
  * every tool tile on the Tools screen has search keywords.

No network, no file changes, no commits. Exit code 1 when something fails.
Adapted from the 2026-11 all-in-one patch's audit script, whose checks targeted code this repo does not have.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
LOCALES = ("hi", "kn", "ml", "ta", "te")
TOOLS = ROOT / "app/src/main/java/com/example/scanpro/feature/tools"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
TOOLS_NS = "{http://schemas.android.com/tools}"
FORBIDDEN_PERMISSIONS = {
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.QUERY_ALL_PACKAGES",
}


def strings_in(folder: Path, errors: list[str]) -> dict[str, str]:
    """name -> text of every <string> in the folder's XML files (translatable ones only)."""
    found: dict[str, str] = {}
    for path in sorted(folder.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as err:
            errors.append(f"{path.relative_to(ROOT)} does not parse: {err}")
            continue
        for item in root:
            if item.tag != "string" or item.get("translatable") == "false":
                continue
            name = item.get("name", "")
            if name in found:
                errors.append(f"{folder.name}: string {name} is defined twice")
            found[name] = "".join(item.itertext())
    return found


def placeholders(text: str) -> list[str]:
    return sorted(re.findall(r"%(?:\d+\$)?[sdfx]", text.replace("%%", "")))


def audit() -> tuple[list[str], list[str]]:
    errors: list[str] = []
    notes: list[str] = []

    default = strings_in(RES / "values", errors)
    notes.append(f"{len(default)} translatable default strings")
    for locale in LOCALES:
        translated = strings_in(RES / f"values-{locale}", errors)
        missing = sorted(set(default) - set(translated))
        for name in missing:
            errors.append(f"{locale}: missing translation for {name}")
        for name in sorted(set(default) & set(translated)):
            if placeholders(default[name]) != placeholders(translated[name]):
                errors.append(f"{locale}: placeholders differ for {name}")
        extra = sorted(set(translated) - set(default))
        for name in extra:
            errors.append(f"{locale}: {name} has no default (English) string")

    if MANIFEST.is_file():
        manifest = ET.parse(MANIFEST).getroot()
        for element in manifest.iter("uses-permission"):
            name = element.get(f"{ANDROID_NS}name", "")
            removed = element.get(f"{TOOLS_NS}node") == "remove"
            if name in FORBIDDEN_PERMISSIONS and not removed:
                errors.append(f"Manifest asks for {name}: needs a security review and an owner decision")
    else:
        errors.append("AndroidManifest.xml not found")

    screen = (TOOLS / "PdfToolsScreen.kt").read_text(encoding="utf-8")
    landing = (TOOLS / "ToolsLanding.kt").read_text(encoding="utf-8")
    tool_ids = re.findall(r'PdfToolCard\("([a-z0-9_]+)"', screen)
    keywords = set(re.findall(r'^\s*"([a-z0-9_]+)" to "', landing, flags=re.M))
    if len(tool_ids) != len(set(tool_ids)):
        errors.append("Two tool tiles share an id")
    for tool in sorted(set(tool_ids)):
        if tool not in keywords:
            errors.append(f"Tool {tool!r} has no search keywords in ToolsLanding.TOOL_KEYWORDS")
    notes.append(f"{len(set(tool_ids))} tool tiles checked")
    return errors, notes


def main() -> int:
    errors, notes = audit()
    lines = ["## ScanPro source audit", ""] + [f"- {n}" for n in notes] + [f"- ERROR: {e}" for e in errors]
    lines += ["", "**Status:** " + ("FAILED" if errors else "PASS")]
    report = "\n".join(lines) + "\n"
    print(report)
    summary = os.getenv("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(report)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
