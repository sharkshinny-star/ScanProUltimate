#!/usr/bin/env bash
# Prints a Markdown table of the release outputs' sizes and fails when the arm64 APK is over budget.
# Usage: scripts/ci/size-report.sh <apk-dir> <bundle-dir>
set -euo pipefail
APK_DIR=$1
BUNDLE_DIR=$2
# Raise this deliberately, in the same change that adds the bytes, so growth is a decision rather than drift.
ARM64_BUDGET_MB=40

mb() { awk -v b="$(stat -c %s "$1")" 'BEGIN { printf "%.1f", b / 1048576 }'; }

echo "### Release output sizes"
echo
echo "| Output | MB |"
echo "|---|---|"
for f in "$BUNDLE_DIR"/*.aab "$APK_DIR"/*.apk; do
  [ -f "$f" ] || continue
  echo "| $(basename "$f") | $(mb "$f") |"
done
echo

arm64=$(ls "$APK_DIR"/*arm64-v8a*.apk | head -1)
size=$(mb "$arm64")
if awk -v s="$size" -v b="$ARM64_BUDGET_MB" 'BEGIN { exit !(s > b) }'; then
  echo "**The arm64 APK is ${size} MB, over the ${ARM64_BUDGET_MB} MB budget in scripts/ci/size-report.sh.**"
  exit 1
fi
echo "arm64 APK: ${size} MB (budget ${ARM64_BUDGET_MB} MB)"
