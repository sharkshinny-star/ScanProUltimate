# Artemis screen test plan (device pass)

Purpose: drive every ScanPro screen on a real phone or emulator with Artemis and record issues. Nothing here has been run yet
(the cloud session that wrote it had no Android SDK or device). It closes part of R7, R19, R24, R25 and R36 in `AUDIT_FINDINGS.md` once run.

## Setup (on your computer)

```bash
cd ScanProUltimate && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
cd ../artemis && uv run artemis init      # choose a model, add its API key
uv run artemis doctor                     # confirms the phone is seen
```

Use a 360 dp phone first, then a tablet or foldable emulator. Keep the camera pointed at a printed page, a receipt and a business card.

## How to run

One task per screen, Pro profile so each step is checked:

```bash
uv run artemis run "<prompt below>" --profile pro
```

Or ask Claude Code with the Artemis MCP installed (`uv run artemis mcp --install claude`) and paste the prompts. For every task add:
"Take a screenshot at the end. Report anything clipped, overlapping, untranslated, unreachable or crashing. Do not change settings I did not name."

## Prompts

| # | Screen | Prompt |
|---|---|---|
| 1 | Launch, Welcome | Force-stop ScanPro, open it, pick a language, tap Continue, finish the welcome pages. Check no black frame and no restart on Continue (F66). |
| 2 | Home | Open ScanPro. Scroll Home to the bottom, tap one tip and the What's new sheet if shown. Report truncated text. |
| 3 | Camera | Tap Scan, grant camera permission, switch modes (Document, ID, Book, QR, Text), toggle flash in the menu, rotate to landscape and back. |
| 4 | Review | Capture 3 pages of the printed page. In review open Adjust, Filters, Crop, Retake page 2, delete page 3, press Back and read the prompt. Check the blur and glare flags (R25). |
| 5 | Scan saved | Save the scan. In the "Scan saved" sheet check the Searchable PDF badge, Copy text, Share as PDF, Add pages, Scan another. Repeat for a receipt (total shown?) and a business card (Add to contacts?) (R36). |
| 6 | Files | Open Files. Create a folder, move the scan into it, switch list and grid, sort, search for a word on the page, open the ⋮ menu on a card. |
| 7 | Document | Open the scan. Rename it, reorder pages by dragging, open Export, Markup, Eraser, Sign, Watermark. Rotate the phone inside each editor and check work is kept; press Back and check a discard prompt (R12). |
| 8 | PDF viewer | Open a PDF from Files app with ScanPro. Zoom, search text, jump to a page, rotate. |
| 9 | Tools | Open Tools. Run Merge, Split, Compress, Protect with a password of fewer than 8 characters (must be refused), Unlock. Check progress and Cancel. |
| 10 | Form fill | Tools, Fill form, open a sample PDF form, type in two fields, press Back (must ask before discarding). |
| 11 | QR | Scan a QR code holding a URL, a Wi-Fi code and a UPI code. Check the result sheet and that nothing opens without a tap. |
| 12 | Recycle bin, Kept scans | Delete a document, open Recycle bin, restore it, delete again, Empty bin and read the confirmation. Open Kept scans. |
| 13 | Settings | Open every Settings section. Switch theme (Light, Dark, System), each of the 5 colour themes, and each language. Check each screen stays in place and nothing is cut off. |
| 14 | App lock | Turn on app lock, send the app to the background for a minute, reopen, unlock. Open a locked folder. |
| 15 | Backup | Settings, Backup: make a backup with a short password (must be refused), then a valid one, delete a document, restore. Check the document returns. |
| 16 | Accessibility | Set font size to the largest and display size to the largest, then repeat prompts 2, 6, 7 and 13. Report clipped or unreachable controls. |
| 17 | Languages | In Hindi and Tamil, open Home, Camera, Review, Scan saved, Settings. Report any English left (R13, R34) and clipped text. |
| 18 | Stress | Run: "Scan 10 pages in Document mode quickly, then save." Check memory, no crash (`adb logcat -b crash`). |

## Recording results

Log each issue with the prompt number, device, a screenshot name and one line of what is wrong. Add confirmed ones to
`AUDIT_FINDINGS.md` as new R* IDs and list the run in `BUILD_AND_TEST.md` "Latest results".
