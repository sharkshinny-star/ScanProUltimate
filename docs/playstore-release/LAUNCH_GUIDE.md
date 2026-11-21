# ScanPro launch guide

19 November 2026. Written for the owner: every step left before the Play release, with the text to paste.

ScanPro is not ready to publish yet: the code builds and its tests pass, but the steps below need you, your phone or
Play Console. Work through them in order and tick each one off.

## Where things stand

Done in the project (branch `claude/awesome-clarke-ussjo3`):

- Privacy policy filled in, except your developer name.
- Script that creates the upload key (`scripts/setup-upload-key.sh`).
- The Play version ships without "All files access"; the build enforces and checks this.
- Store listing text, app icon, feature graphic and seven screenshots.
- 1,145 unit tests run, 0 failed; a Play bundle built without the permission.

Left for you, in order:

- [ ] Add your developer name to the privacy policy and host it on GitHub Pages
- [ ] Run the keystore script and build the signed bundle
- [ ] Upload the bundle to internal testing and install it on your phone
- [ ] Do the phone pass and report anything that fails
- [ ] Record the foreground service video and capture one real camera screenshot
- [ ] Fill in the Play Console forms
- [ ] Enter the store listing text and upload the graphics
- [ ] Confirm version code 7 is higher than anything uploaded before
- [ ] Run a closed test (12 testers, 14 days) if your account is a personal one created after 13 November 2023
- [ ] Submit for production and wait for Google's review

## 1. Privacy policy

The page is `docs/privacy-policy/index.html`. It will live at `https://sharkshinny-star.github.io/scanpro-privacy/`.

| Field | Value |
| --- | --- |
| Effective date | 19 November 2026 (change it if you publish later) |
| Developer name | **Not filled: you add it, in two highlighted places** |
| Contact | sharkshinny@gmail.com |
| Children | Not directed at children under 13 |
| File access | The Google Play version does not ask for access to all files |

Steps:

1. Put your developer name (as on your Play Console account) in the two highlighted places, then delete the yellow box at the top.
2. On GitHub, create a **public** repository named exactly `scanpro-privacy` and upload `index.html` to it.
3. In that repository open Settings > Pages, choose "Deploy from a branch", pick `main` and `/ (root)`, and save.
4. Open the address above in a browser to confirm it is live.
5. Paste the address into Play Console as the privacy policy URL.

## 2. Keystore and signed bundle

Run these on your Mac, in the project folder. Never share the key file or its password, and never paste them into a chat.

If ScanPro was ever uploaded to Play before, do not make a new key: pass the old key file's path to the script instead.

Get the latest project files:

```bash
git pull
```

Create the key (it asks for your name and a password of 12 or more characters):

```bash
bash scripts/setup-upload-key.sh
```

Or, with a key you already have:

```bash
bash scripts/setup-upload-key.sh /path/to/your-existing-key.jks
```

Build the signed bundle for Play:

```bash
JAVA_HOME="/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home" ./gradlew :app:bundleRelease -Pscanpro.allFilesAccess=false -Pscanpro.privacyPolicyUrl=https://sharkshinny-star.github.io/scanpro-privacy/
```

A good build ends with the line "Play bundle check: app-release.aab does not declare MANAGE_EXTERNAL_STORAGE".

Check that it is signed (it should print your name and a certificate, not "Not a signed jar file"):

```bash
"/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home/bin/keytool" -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

Afterwards, save the password in a password manager and copy `~/scanpro-keys/scanpro-upload.jks` to a second safe place.

The bundle to upload is `app/build/outputs/bundle/release/app-release.aab`.

## 3. Upload to internal testing

Internal testing has no Google review, so the build is on your phone within minutes. Menu names may differ slightly in Play Console.

1. Open play.google.com/console and select ScanPro. If it is not there, choose Create app: name it, pick App and Free. The package name is `com.scanpro.app`.
2. Go to Testing > Internal testing.
3. On the Testers tab, create an email list with the Google account on your phone, tick it and save.
4. On the Releases tab, choose Create new release.
5. Accept Play App Signing if asked, keeping the default where Google manages the app signing key.
6. Upload `app-release.aab` and wait for it to process.
7. Paste the release notes (section 6 of this guide).
8. Choose Next, then Save and publish.
9. On the Testers tab, copy the "Join on the web" link, open it on your phone, accept, and install from the Play Store.

What to watch for:

- Warnings are usually fine; errors are not. Send the exact text of any error.
- If Play asks for an "All files access" declaration, stop: the wrong bundle was uploaded.
- "Signed with the wrong key" means an earlier upload used a different key.
- Version code is 7. Play rejects a version code it has seen before.

## 4. Phone pass

About 30 minutes on the build installed from internal testing. Uninstall any older copy first so you see the first-run screens.

First run

- [ ] 1: Welcome screens appear and you can pick a language
- [ ] 2: The permission page asks for camera and notifications only; nothing asks for access to all files
- [ ] 3: "Not now" still lets you into the app

Scanning

- [ ] 4: Scan a real page on a desk: the outline finds the page and the crop is straight
- [ ] 5: Scan three pages in one go, reorder them, retake one, save
- [ ] 6: Try each filter; the text stays readable
- [ ] 7: Open the saved document and search for a word that is on the page

PDF tools (use a PDF from your Downloads)

- [ ] 8: Merge two PDFs
- [ ] 9: Compress one and open the result
- [ ] 10: Protect one with a password, then open it in another PDF app
- [ ] 11: Redact a word, then search for it in the result in another PDF app; it must not be found
- [ ] 12: Sign a PDF and share the result to another app

Files tab

- [ ] 13: It shows a storage overview and no "allow access" card
- [ ] 14: Importing a file through the picker works

Safety

- [ ] 15: Turn on the app lock, leave, come back: it asks for your fingerprint or screen lock
- [ ] 16: Back up all documents with a password, delete one, restore the backup: it comes back
- [ ] 17: Delete a document and restore it from the Recycle bin

Robustness

- [ ] 18: In aeroplane mode, scanning and text recognition still work
- [ ] 19: Rotate the phone during a scan and on the document screen: nothing is lost
- [ ] 20: Settings > privacy policy link opens your hosted page

Blockers (do not publish until fixed): any crash or freeze, any lost scan or document, a redacted word that can still
be found, a backup that does not restore, any prompt for all-files access. Cosmetic issues can wait for an update.

For anything that fails, note the step number, what you saw, and a screenshot.

## 5. Play Console form answers

All are under Policy and programmes > App content. Question wording may differ slightly. Answers marked "your call"
need your judgement. The full version with notes is `PLAYSTORE_DECLARATIONS.md`.

| Form | Answer |
| --- | --- |
| Privacy policy | `https://sharkshinny-star.github.io/scanpro-privacy/` |
| Ads | No, my app does not contain ads |
| App access | All functionality is available without special access |
| Content rating | Category Utility / Productivity; answer No to every question |
| Target audience | 13–15, 16–17, 18 and over; does not appeal to children |
| Advertising ID | No, my app does not use an advertising ID |
| Financial features | My app doesn't provide any financial features (your call) |
| Health apps | My app doesn't provide any health features (your call) |
| News, Government, COVID-19, Generative AI | No to each |
| Account deletion | Not applicable: there are no accounts |
| Trader status | Trader if you publish as a business, Non-trader if as a private individual (your call, a legal one) |

On financial features: the app only makes a QR code for a UPI ID and hands scanned payment codes to a payment app you
choose. If a reviewer disagrees, the nearest category is "Mobile payments and digital wallets". A trader's address and
phone number become public on the listing from 1 March 2027.

### Data safety

Submitted on 8 November; compare it with this.

| Question | Answer |
| --- | --- |
| Does the app collect or share user data? | Yes (through Google's ML Kit library) |
| Is all data encrypted in transit? | Yes |
| Can users request deletion? | No: there is no account and you hold nothing (your call) |
| App info and performance > Diagnostics | Collected, not shared, required, purpose: Analytics |
| App info and performance > Other app performance data | Same answers |
| Device or other IDs | Same answers |
| Photos, videos, files and docs | Not collected (processed on the phone only) |
| Personal info, financial info, contacts, location, messages, audio, health | Not collected |
| Crash logs | Not collected (your call; the cautious alternative is "collected, optional") |

### Foreground service (data sync)

Use case: Data sync > Local processing > Other.

Description:

```text
When the user starts a long task on their own document (for example compressing or merging PDFs, recognising the text of a multi-page scan, or exporting), ScanPro keeps it running if they switch to another app and shows a notification with the task's progress. The work is done on the phone; nothing is uploaded.
```

Impact if deferred:

```text
The task the user just started would not finish until they come back; a half-written output file could be lost.
```

Impact if interrupted:

```text
The output file would be incomplete and the user would have to start again.
```

This form also needs the video in section 8.

## 6. Store listing

Enter these under Grow > Store presence > Main store listing. The source is `PLAYSTORE_STORE_LISTING.md`. Before
publishing, check on your phone that the description's claims hold (recognition languages, widget, Quick Settings tile,
translation); none has been confirmed on a device.

App name (28 of 30 characters):

```text
ScanPro: Scanner & PDF Tools
```

Short description (71 of 80 characters):

```text
Scan documents to PDF, recognise text and work with PDFs on your phone.
```

Full description (2,705 of 4,000 characters):

```text
ScanPro turns your phone into a document scanner and PDF toolkit. Your scans, recognised text and documents are kept in the app's private storage on your phone. There is no account and no advertising.

SCAN
• Camera scanning with automatic page-edge detection, perspective correction and crop you can adjust by hand
• Scan modes for documents, books (split a two-page spread) and ID cards (front and back on one page)
• Page filters (such as Original, Magic Colour and black and white), plus rotate, reorder and retake
• Import photos with Android's photo picker, or PDFs and images shared from other apps

RECOGNISE TEXT (OCR)
• Recognises Latin, Devanagari, Chinese, Japanese and Korean text on the phone using Google ML Kit
• Search your library by the words in your scans, then copy, correct or export the text
• Make an existing PDF searchable
• Text recognition is not perfect: check important details such as numbers and names before you rely on them

PDF TOOLS
• Merge, split, extract, insert, rotate and organise pages; remove blank pages
• Compress PDFs and images; resize, crop and convert images
• Convert images and Office files to PDF, and PDF to images, Word, Excel or text
• Add page numbers, headers and footers, watermarks and several pages per sheet
• Fill PDF forms, flatten forms and comments, crop pages, convert to greyscale, invert colours
• Edit the text of a PDF, and redact words or areas: redacted pages are redrawn as pictures and the saved copy is checked before it is kept
• Sign PDF: draw your signature and place it on a page as an image. This is a visual signature, not a certified digital signature
• Protect a PDF with a password, or remove a password you know

QR AND BARCODES
• Scan QR codes and barcodes, with a preview before a web link opens
• Create QR codes, including a UPI payment code for your own UPI ID. ScanPro does not make payments: paying opens the UPI app you choose
• Keep a scan history, stored encrypted on your phone, or turn it off

PRIVACY AND SECURITY
• Optional app lock with your biometrics or screen lock, and locked folders
• Password-protected backups you can export and restore
• Your documents and recognised text are not uploaded. Google ML Kit, which powers text recognition, barcode scanning and translation, sends Google diagnostic information (such as device model and performance data, not your images or text). Language models and the optional translation feature are downloaded through Google Play when first needed

ALSO
• Translate recognised text on the phone after a one-time language download
• Home-screen widget and Quick Settings tile to start a scan
• App available in English, Hindi, Tamil, Telugu, Kannada and Malayalam
```

Release notes for version 4.2.0:

```text
• Redaction now checks each saved copy and refuses to keep it if a redacted word is still inside the file
• Rebuilt page, image, convert and edit tools, each with a preview of the result before it runs
• Resizable home-screen widget
• Fixes and clearer wording
```

| Field | Value |
| --- | --- |
| App or game | App |
| Category | Productivity |
| Contact email | sharkshinny@gmail.com |
| Privacy policy | `https://sharkshinny-star.github.io/scanpro-privacy/` |
| Listing language | English only, until a native speaker has read the five Indian-language translations |

## 7. Graphics and screenshots

All files are in `docs/playstore-release/assets/`. Upload them under Grow > Store presence > Main store listing > Graphics.

| Asset | File | Size |
| --- | --- | --- |
| App icon | `play-icon-512.png` | 512 × 512 |
| Feature graphic | `play-feature-graphic-1024x500.png` | 1024 × 500 |
| Screenshot 1 | `screenshots/01-library.png`: Keep every scan organised | 1080 × 1920 |
| Screenshot 2 | `screenshots/02-search.png`: Find documents by the words inside them | 1080 × 1920 |
| Screenshot 3 | `screenshots/03-pdf-tools.png`: A full PDF toolkit on your phone | 1080 × 1920 |
| Screenshot 4 | `screenshots/04-organise-pages.png`: Reorder, rotate and remove pages | 1080 × 1920 |
| Screenshot 5 | `screenshots/05-document.png`: Crop, clean up and edit each page | 1080 × 1920 |
| Screenshot 6 | `screenshots/06-qr.png`: Create and scan QR codes | 1080 × 1920 |
| Screenshot 7 | `screenshots/07-app-lock.png`: Lock the app with your fingerprint | 1080 × 1920 |

The seven screenshots are the app's real screens rendered from its code with sample documents, not photos of a phone.
The one missing is the camera scanning a page, which only a real phone can capture. Play allows eight, so take that one
yourself and put it first.

If you retake screenshots on your phone:

- Use the release build, light theme, English, portrait.
- Use only sample documents: no real ID cards, bank letters, UPI IDs, names or addresses.
- Tidy the status bar: full battery, no notification icons.
- The long side may be at most twice the short side, so crop a little off the top or bottom.
- No claims such as "best" or "#1", and no other apps' logos.

## 8. Foreground service video

One continuous screen recording, 30 to 60 seconds, no voice-over. Use the release build and a PDF of 50 pages or more,
so the task runs for at least 10 seconds. Clear your notifications and turn on Do Not Disturb first.

| Time | What you do | What the reviewer sees |
| --- | --- | --- |
| 0:00 | Open ScanPro from the home screen | The app launching |
| 0:05 | Tap Tools, then Compress PDF | The feature that needs the background service |
| 0:10 | Pick the large PDF | The file name and page count |
| 0:15 | Tap Compress | The progress indicator starting |
| 0:18 | Press Home straight away | The app leaving the screen while the task runs |
| 0:20 | Pull down the notification shade, pause 3 seconds | ScanPro's notification with the task's progress |
| 0:28 | Wait until the progress moves or completes | The work continuing in the background |
| 0:40 | Tap the notification or reopen ScanPro | The result screen with size before and after |
| 0:50 | Stop recording | |

Then trim anything personal, upload to YouTube as **Unlisted** (not Private), and paste the link into the foreground
service form.

On a dry run, check two things that were not verified: the notification shows progress while the app is in the
background, and returning to the app lands on the result screen.

## 9. Not verified, and what can wait

Not verified:

- The keystore script has not been run on macOS, and no signed build exists yet.
- Lint was not re-run after the build change.
- The automatic build check on GitHub that the last code push triggered has not been looked at.
- None of the recent features, including the build without all-files access, has been seen on a phone.
- The store screenshots and graphics are awaiting your approval.

What the Play version does differently: on Android 11 and newer it has no "Files on this phone" list, so people open
files through the normal picker. Android 10 and older keep the list. The declaration draft is kept in
`PLAY_ALL_FILES_ACCESS.md` in case you want the feature back in a later update.

Can wait until after launch:

- A native speaker's read of the Hindi, Tamil, Telugu, Kannada and Malayalam text.
- The stashed storage-permission change and the stray `widget_scan.xml` edit on your Mac.
- Tablet screenshots.

More detail is in the project: `REMAINING_WORK.md` and the other files in `docs/playstore-release/`.
