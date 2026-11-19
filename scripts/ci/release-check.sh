#!/usr/bin/env bash
# Release checks on the built artefacts (Play release preparation, F210). Prints a Markdown report to stdout.
#   1. 16 KB page size: every .so in the App Bundle, by ABI, with the alignment of its LOAD segments
#      (64-bit ABIs need 2**14 = 0x4000 or more; armeabi-v7a is 32-bit and not covered by the requirement).
#   2. ZIP alignment of the sideload APKs for 16 KB pages: zipalign -c -P 16 -v 4.
#   3. The merged release manifest: permissions, services and exported components.
# Usage: scripts/ci/release-check.sh <aab-dir> <apk-dir> <merged-manifest>
set -uo pipefail
aab_dir=$1; apk_dir=$2; manifest=$3
ndk=${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}
readelf=$(ls "$ndk"/toolchains/llvm/prebuilt/*/bin/llvm-readelf 2>/dev/null | head -1)
build_tools=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)
zipalign="$build_tools/zipalign"
fail=0

echo "## Release check"
echo
echo "Tools: llvm-readelf: \`${readelf:-missing}\`; zipalign: \`$zipalign\`"
echo
echo "### 1. Native libraries in the App Bundle (LOAD segment alignment)"
echo
work=$(mktemp -d)
for aab in "$aab_dir"/*.aab; do
  echo "Bundle: \`$(basename "$aab")\` ($(stat -c %s "$aab") bytes)"
  echo
  unzip -q -o "$aab" '*/lib/*' -d "$work/$(basename "$aab")" 2>/dev/null || true
done
echo "| ABI | Library | Smallest LOAD align | 16 KB |"
echo "|---|---|---|---|"
while IFS= read -r so; do
  abi=$(basename "$(dirname "$so")")
  name=$(basename "$so")
  align=$("$readelf" -lW "$so" 2>/dev/null | awk '$1=="LOAD"{print $NF}' | sort -u | head -1)
  dec=$((align))
  if [ "$abi" = "armeabi-v7a" ] || [ "$abi" = "x86" ]; then verdict="n/a (32-bit)"
  elif [ "$dec" -ge 16384 ]; then verdict="PASS"
  else verdict="FAIL"; fail=1; fi
  echo "| $abi | $name | $align | $verdict |"
done < <(find "$work" -name '*.so' | sort)
echo
echo "### 2. APK ZIP alignment for 16 KB pages"
echo
echo "| APK | zipalign -c -P 16 |"
echo "|---|---|"
for apk in "$apk_dir"/*.apk; do
  if "$zipalign" -c -P 16 -v 4 "$apk" > "$work/za.txt" 2>&1; then r=PASS; else r=FAIL; fail=1; fi
  echo "| $(basename "$apk") | $r |"
done
echo
echo "### 3. Merged release manifest"
echo
echo '```'
grep -oE '<uses-permission[^>]*android:name="[^"]+"[^>]*>' "$manifest" | sed -E 's/.*android:name="([^"]+)".*/permission: \1/' | sort -u
grep -oE '<(service|receiver|provider|activity)[^>]*>' "$manifest" | grep -E 'exported="true"|foregroundServiceType' | sed -E 's/\s+/ /g' | cut -c1-240
echo '```'
echo
echo "Result: $([ $fail -eq 0 ] && echo 'all checked libraries and APKs PASS' || echo 'FAILURES above')"
rm -rf "$work"
exit $fail
