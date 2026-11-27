# Scanning code before the November 2026 detection and enhancement work

A frozen copy of `app/src/main/java/com/example/scanpro/scanner/` and `imaging/` exactly as they were at commit
`93148f0`, before F168-F172 (hard-shadow detection, the straight-edge search, the half-size pipeline stages, the
gradient snap, the filter illumination fix and the Crop/Redact tools). Kept at the owner's request as an in-tree
archive; it sits under `docs/`, outside every source set, so nothing compiles it.

The same history is in git: `git show 93148f0:app/src/main/java/com/example/scanpro/scanner/DocumentEdgeDetector.kt`,
or `git checkout 93148f0 -- app/src/main/java/com/example/scanpro/scanner` to restore for real. The stages since:
`6565874` (F168), `16ee748` (F169), `448ddd8` (F170), `bad0333` (F171), `e594924` (F172).

Do not edit these files; they are a snapshot, not code to maintain.
