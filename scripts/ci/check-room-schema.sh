#!/usr/bin/env bash
# Fails when the Room database version and the exported schema files disagree, because Room's auto-migrations
# (and the migration tests) need app/schemas/<db>/<version>.json for every version.
#   1. the current version has a schema file;
#   2. every version from 1 to the current one has a schema file;
#   3. after a build, no exported schema differs from what is committed (a changed table without a version bump
#      or a new version whose file was not committed shows up as a diff). Pass --after-build for this step.
set -euo pipefail

db_source="app/src/main/java/com/example/scanpro/data/DocumentDatabase.kt"
schema_dir="app/schemas/com.example.scanpro.data.ScanRoomDatabase"

version=$(sed -n 's/.*DATABASE_VERSION *= *\([0-9][0-9]*\).*/\1/p' "$db_source" | head -n 1)
if [ -z "$version" ]; then
  echo "Could not read DATABASE_VERSION from $db_source" >&2
  exit 1
fi

if [ "${1:-}" = "--after-build" ]; then
  if [ -n "$(git status --porcelain -- app/schemas)" ]; then
    echo "The build changed the exported Room schemas. Commit the updated files (and bump the version if a table changed):" >&2
    git status --short -- app/schemas >&2
    git diff -- app/schemas | head -n 80 >&2
    exit 1
  fi
  echo "Room schemas match the committed files (version $version)."
  exit 0
fi

missing=0
for v in $(seq 1 "$version"); do
  if [ ! -f "$schema_dir/$v.json" ]; then
    echo "Missing $schema_dir/$v.json (database version is $version)" >&2
    missing=1
  fi
done
[ "$missing" -eq 0 ] || exit 1
echo "Room schema files 1..$version are present."
