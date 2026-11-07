# Archived code (not compiled)

- `FirebaseCloudClient.kt.txt`: Firebase cloud client (408 lines) from the older ScanPro repo (commit `3e6c84c`, 17 Oct 2026),
  kept for a possible future cloud-sync feature. Stored as `.txt` so Gradle ignores it.

It is **not used**: Ultimate is offline-first with no accounts or network services (see `CLAUDE.md` and `FEATURE_MATRIX.md`).
Using it would be a deliberate, documented change to those rules. It depends on Hilt and Firebase, and on `PageFilter` and
`ScanDocument` from the old data model, so it needs updating before use.
The original Firebase API key was removed; supply your own.
