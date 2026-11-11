# Design note: keep undo history across sessions (item 7)

Status: **proposal, nothing built.** `CLAUDE.md` asks for security-sensitive and architectural changes to be written down
first. Owner approval is needed on the three questions at the end.

## 1. Today

- `data/EditHistory` keeps each document's undo and redo stacks in memory only. The app closing, or Android killing it, clears them.
- A step is the document's **list of pages as they were**: edit settings (filter, crop, rotation, adjustments), mark-up, OCR
  text, corrections and order. It holds **no pixels**. Undo draws again from the original photo, and only for pages whose look
  differs (`PageEditor.step`, `sameRendering`). So the picture files from before an edit do **not** need to be kept for undo.
- Structural edits clear the history (`history.clear`): adding, deleting, merging, extracting or replacing pages, and
  splitting a book spread. Steps therefore always describe the same set of pages as the document.
- Limits: 20 steps and about 6 MB of estimated size per document (the oldest steps go first; the newest is always kept).
- The one thing that points at files outside the page list is a **signature image** (`signatures/sig_*.png`).
  `PageEditor.cleanUnusedSignatures` runs when a document is closed and deletes signature images that neither the document
  nor `history.retained()` uses.
- Unrelated and already persistent: the **edit journal** (`filesDir/edit_journal/`) replays queued edits after the process
  dies. It does not cover undo or redo. It is a useful precedent: one JSON file per item, written on one serial IO thread.

Correction to what I first said in chat: I expected old picture files to pile up and be the main risk. The code shows that is
not the case. The real risks are the signature clean-up and stale history (sections 4 and 5).

## 2. Proposal

Save each document's stacks to **`<document folder>/undo.json`** and load them when the document is opened.

- **Why a file and not Room:** a new table means a schema bump to version 4. An older build that meets version 4 moves the
  whole library aside (the downgrade path behind item 5), so every schema bump makes that more likely. A file costs no
  migration, is deleted with the document's folder (trash purge, delete) without extra code, and older builds ignore it.
- **Format:** `{"v":1,"current":"<fingerprint>","undo":[[page,...],...],"redo":[[page,...],...]}`. Each page is encoded with the same
  JSON codecs the `pages` table already uses for crop, adjustments, annotations and OCR words, so there is one definition of a page.
- **When it is written:** after `record`, `undo`, `redo`, `rollback` and `clear`, on a serial IO thread, to `undo.json.tmp` and then
  renamed over `undo.json` (never half written). `clear` deletes the file. A failed write is logged and ignored; the
  in-memory history still works, as today.
- **When it is read:** once per document per process, when the document is opened (`DocumentViewModel` init) and at the start of
  `step` and `cleanUnusedSignatures`. It is not read at app start, so a large library costs nothing.
- **No new dependency, no schema change, no new permission.**

## 3. Size

Same caps as today (20 steps, about 6 MB estimated per document), applied before writing. Typical files are a few KB; the
cap bounds the worst case at roughly the size of the document's OCR text and mark-up, once per step that changed.

## 4. Data-safety risks and how each is handled

1. **Signature clean-up deletes a file a saved step needs.** After a restart the in-memory history is empty, so today's
   `cleanUnusedSignatures` would delete images that `undo.json` still refers to. *Handling:* load the history before the
   clean-up runs, and on load drop any step that refers to a missing signature file (and every step older than it).
   Test: restart, remove a signature, close the document, undo still brings it back.
2. **History that no longer matches the document** (restored from a backup, changed by an older build, edited outside the
   history). *Handling:* `current` stores a fingerprint of the document's pages at the last write (page ids plus their encoded
   edit settings, text and mark-up; title, tags and folder are excluded so renaming does not discard history). On load,
   a mismatch discards the file. `step` already refuses steps whose page ids do not fit the document.
3. **Corrupt or unknown file.** Unknown `v`, bad JSON or a missing page file: discard the file, start with empty history, never
   crash, never touch the document.
4. **Nothing is silently deleted.** Only `undo.json` is ever deleted by this feature, and only when the history is cleared or the
   document goes. No page, photo or PDF file is touched.

## 5. Security and privacy

- `undo.json` holds copies of OCR text, text corrections and mark-up text for up to 20 earlier states. That is more readable
  text on disk, for longer, than today. It includes text from before a redaction: a step taken before a redaction still has the
  OCR text of the page as it was. The original photo in `source/` already holds that content, so this adds a second,
  easier-to-read copy, not a new kind of exposure. Like the rest of the library it is **not encrypted at rest** (R20, owner decision).
- It stays in app-private storage. Cloud backup already excludes all files (`backup_rules.xml`, `data_extraction_rules.xml`);
  device-to-device transfer would carry it with the library, which is wanted.
- Encrypted `.scanpro` backups, PDF exports and shared files do **not** include it. A restored backup starts with empty history.
- Option if the owner wants less on disk: drop the OCR text and words from saved steps and re-run recognition only when
  needed. That makes undo of a text edit lossy, so it is not proposed.

## 6. Tests

JVM and Robolectric, using the existing fakes (`RepositoryTestBase`):

- Codec round trip of every page field; unknown `v`, bad JSON and truncated file are discarded.
- Edit, then a second `DocumentRepository` on the same files (a restart): undo and redo work and match the first session.
- Fingerprint mismatch discards; a rename does not.
- Missing signature file drops that step and the older ones; `cleanUnusedSignatures` after a restart keeps a signature a step needs.
- `clear` deletes the file; deleting the document removes it with the folder; a failed write does not break an edit.
- Size cap applies to the saved file. `undo.json` is absent from `exportEncryptedBackup`.

Nothing here can run in the cloud container used so far (Gradle cannot resolve the Android plugin); CI is the only check, about
20 runner minutes per run.

## 7. Effort and order

One new class (`UndoStore`), small changes to `EditHistory` (load and persist hooks), `PageEditor` (`ensureLoaded` in `step`
and `cleanUnusedSignatures`), `DocumentViewModel` (load on open), `DocumentStore` (file path) and the codecs (make the page
encoding reachable from outside `DocumentDatabase.kt`). Estimate: one session plus one or two CI cycles. Device check: edit,
force-stop the app, reopen, undo.

## 8. Decisions needed from the owner

1. Is it acceptable to keep up to 20 earlier text states, including text from before a redaction, in plain app-private storage?
2. Are the limits (20 steps, about 6 MB per document) right, or should saved history be shorter (for example 10)?
3. Should history of a document in the Recycle bin be kept until it is purged (proposed), or cleared when it is trashed?
