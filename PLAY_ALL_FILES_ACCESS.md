# Google Play: All files access declaration (draft)

Status: **not filed.** Only the owner can do this in Play Console; nothing here has been submitted. Written 2026-11-17 from
the code on `claude/awesome-clarke-ussjo3`. Check each statement against the app before pasting it.

## Where
Play Console, the app, **Policy and programmes > App content > Sensitive app permissions > All files access**, or the
declaration that appears when a bundle with `MANAGE_EXTERNAL_STORAGE` is uploaded. Play also asks for a short video.

## What the app does with the permission (verified in the code)
- Manifest: `MANAGE_EXTERNAL_STORAGE` (Android 11+) and `READ_EXTERNAL_STORAGE` with `maxSdkVersion="29"`, plus
  `requestLegacyExternalStorage` (read by Android 10 only). See the comment in `app/src/main/AndroidManifest.xml`.
- Used for one optional feature, **Files on this phone** (`feature/phonepdfs/`): a read-only list of the PDFs, images,
  Word, Excel, PowerPoint and text files in shared storage, on Home and in Files, with filters, search, sort and thumbnails.
- Asked for once at start (F196, owner's request, 2026-11-18): on the welcome's permission page, which explains it next to
  the camera and notifications, or by a one-time prompt for people past the welcome; each leads to Android's "All files
  access" page. A card in Files explains it too. The app works without it; the user can say Not now or revoke it in
  system settings.
- Google's permitted use for "document management" reads "locate, access, and edit compatible file types"; ScanPro's
  use is read only (the tools write new files through the picker or app storage). That is the main rejection risk.
  Fallback: a Play build without the permission, `-Pscanpro.allFilesAccess=false` (F210), removes it from the manifest
  and every prompt for it. See docs/playstore-release/PLAYSTORE_PERMISSIONS_AUDIT.md.
- Files are only read. Nothing is changed or deleted without an explicit user action. No file name, path or content is uploaded; the app has
  no analytics or accounts (its only network use is Google's ML Kit models and diagnostics and Play delivery).

## Suggested answers
**Permission use case (pick from Play's list):** Document management apps.

**What core functionality needs it?** ScanPro is a document scanner and PDF toolkit. Users keep PDFs, scans and Office
files in many folders (Downloads, chat apps' document folders, cloud-sync folders, other apps' export folders). Document
management is the point of the Files tab: find any document on the phone, open it in the built-in viewer, and run the
tools on it (merge, compress, sign, convert to or from PDF, organise pages).

**Why not the Storage Access Framework, the Photo Picker or MediaStore?** The picker shows one file at a time and cannot
list a folder tree the user has not chosen. MediaStore does not return other apps' non-media documents (PDF, Office) on
Android 11 and later without this permission. A document list that always shows the user's PDFs and Office files, across
folders, needs to enumerate them. The app still uses the picker everywhere else (import, share, Open with).

**Data handling:** Read only, on the device. No file name, path or content leaves the phone.

**Is it required?** No. It is opt-in; the app is fully usable without it.

## Video (Play asks for one; 30 to 60 seconds, screen recording on a phone)
1. Fresh install, open the app, go through the welcome to its permission page, which explains "Files on your phone".
2. Open Files or Home: the card "See every file on your phone" explaining why.
3. Tap Allow access: Android's "All files access" page, switch ScanPro on, go back.
4. Files on this phone lists PDFs, images and Office files; use the filter chips and search.
5. Open a PDF in the viewer; long-press two PDFs and choose Merge.
6. Settings > Apps > ScanPro > All files access: switch off, and show the app still works.

## Risks to know before filing
- Play can refuse "document management" when the Storage Access Framework would do. Be specific about the list view.
- If it is refused, options (the build switch is done, F210: `-Pscanpro.allFilesAccess=false`): ship the Play bundle without `MANAGE_EXTERNAL_STORAGE` and hide "Files on this
  phone" there (a build flavour or a manifest overlay), keeping the sideload build as it is; or replace the list with a
  folder the user picks once (SAF tree access) that the app remembers.
- Review can take days, so file before the release date and do not rely on this for a fixed launch.
