# ScanPro 4.2.0 — design refresh

A ground-up pass over the app shell so the first minute with ScanPro feels like a flagship Android app.

## Branding
- New launcher icon: a sheet of paper inside scanner brackets with the mint scan line, on the indigo–violet brand gradient. Adaptive layers are vectors (background, foreground, monochrome for themed icons); legacy PNGs and the Play Store icon are rendered from the same artwork.
- The launch screen shows the same mark on the app background, in light and dark, so the hand-off into the app is seamless.
- The mark is drawn in Compose (`BrandMark`) and reused on the welcome, lock, home and settings screens.
- Manrope replaces the system font across the whole type scale (SIL OFL, bundled as one variable file).

## Edge to edge
- The app draws behind both system bars on every Android version; the navigation bar is no longer hidden, so three-button navigation works again.
- Status and navigation bar icons follow the app theme; bar contrast scrims are off.
- Every screen pads its own content with the insets; the bottom bar's colour runs under the gesture area.

## Welcome
- Three-page onboarding on an ambient "aurora" backdrop, with an animated scanning illustration, parallax between pages, a worm page indicator and a gradient call to action. Skipping or finishing fades the welcome away over the home screen.

## Navigation
- Bottom bar with a raised scan button in the middle, so a new scan is one tap from every tab; the side rail on tablets gets the same button.
- Tab switches fade through; pushed screens slide in from the end edge; the camera rises like a sheet.

## Home and files
- Home: greeting, library stats, a search pill, the brand-gradient scan hero, colour-toned quick actions and a horizontal row of recent documents.
- Files: paper-style thumbnails with soft shadows, favourite badges, animated selection, refreshed filter chips and empty states.

## Settings
- Grouped sections with toned icons, segmented controls for theme and PDF quality, a library storage card and an "about" footer.
- New: **Wallpaper colours** (Android 12+) tints the whole app with the device palette.

## Lock screen
- Branded gate that opens the system prompt on its own and keeps the privacy promise in view.

## Polish
- Document editor and PDF viewer share the new chrome; the editor's primary action sits in a filled pill.
- Haptics on the scan button, the editor toolbar and selection toggles.
- Home uses the full width on tablets: one row of eight quick actions.
- Decorative animations (aurora backdrop, scan beam) stay still when system animations are turned off.
- Grid thumbnails decode at a smaller size, cutting memory on long libraries with no visible loss.
- Predictive back (Android 14+) is on through `enableOnBackInvokedCallback`; the nav host's slide transitions play with the gesture.

## Scanning and editing
- **Retake a page** from the review step: the next shot replaces it in place and the review reopens on that page. The editor's "This page" sheet has Retake too: the camera opens for one shot and the new photo takes the page's place, with OCR run again.
- **Live blur and low-light guidance**: the scanner hint reads "Hold steady · the image is blurry" and "Too dark · add light or turn on the flash", with a one-tap **Turn on flash** chip under the hint while the frame is dark. Hints change only after a few frames agree, so one bad frame no longer flickers the text.
- **Undo for page removal**: deleting a page shows "Page removed · Undo"; the files go only once the message is gone. The last page still moves the whole document to the recycle bin, which has its own restore.
- **Share as PNG** next to Share JPG, for forms and apps that reject JPG.
- **Duplicate document** in the editor's Organise section, with an Open action on the confirmation.
- The crop editor's page counter is real and its arrows move between pages; a changed crop is applied before leaving a page.

## Feedback and support
- Settings has a **Feedback & support** section: Rate ScanPro (Play listing), Give feedback, Report an issue and Suggest a feature (email with app version and device details filled in; falls back to the share sheet, then to the clipboard), and Share ScanPro (share sheet with the Play link).
- The support address and the privacy policy and terms links live in `SupportLinks`; the two link rows stay hidden until the URLs are set.
- A **Privacy** row explains, in the app, what stays on the device and what uses the network.
- Give feedback, Report an issue and Suggest a feature confirm with a snackbar saying where the message went (email app, share sheet or clipboard).
- **Google Play in-app review** is requested once, quietly, after the third saved scan; Play decides whether its card appears, and no button in the app promises a review (Rate ScanPro opens the listing).
- A one-time **"How was your scanning experience? 😊"** prompt appears after the fifth saved scan with Share feedback (opens the feedback email) and Maybe later. It is kept apart from the review flow, as Play's guidelines ask.

## Welcome animation
- Entrance choreography: the brand row, the page, the dots and the buttons rise in one after another on first appearance.
- The paper stack drifts gently, and the viewfinder brackets around it start loose and white, then snap tight and mint as each page comes into view, echoing the scanner's own lock-on.
- Feature rows step in one after another as a page settles.
- All of it stays still when system animations are turned off.

## Scanning experience
- The ring round the shutter fills clockwise as the page settles and turns solid mint once it is steady, so "hold still" is visible without reading anything; the shutter dips while it fires.
- The page outline glides instead of jittering (it follows the detector with a small lag), has a faint fill and bracket corners, and turns from white to mint when locked on.
- A soft tick when the page locks on, so eyes can stay on the paper.
- The guidance pill carries a state icon (page, focus, hold-steady hand, flash, done) and its text slides in on change.

## Fixes from the full review
- PDF tools: Compress offers three honest presets, keeps text selectable when the original is kept, cleans up after a failure and stops promptly on Cancel. Split checks ranges against the page count. Protect asks for the password twice and adds no hidden restrictions. Save to device runs in the background, PNG is saved as PNG, out-of-memory ends the tool run with a message, and the form closes before saving so progress and errors are visible.
- Capture: scan imports run as WorkManager work and survive the app being swiped away. Retake works at the page limit. The camera is released during review. Camera errors are shown in every mode with Try again and the compatibility scanner. Launcher shortcuts work. The viewer decodes images at screen size with the right orientation. Curved-page output is capped by the photo size and page renders retry smaller when memory runs out. The cache cleaner covers every temporary file and abandoned sessions.
- Documents: shared PDFs are named after the document, export names keep titles in any script, encrypted backups can be restored from Settings, derived documents keep folder, tags, favourite and OCR language, folder names that differ only in case are one folder, actions refused while the app is busy no longer look done, multi-share says how many documents were left out, search matches each word anywhere in the document, the viewer's Print prints, the merge picker lists every document with search, and the editor status reflects the real PDF state.

## Second round from the full review
- PDF tools: an ordering step before Merge and Images to PDF; page size and quality for Images to PDF; page selection and resolution for PDF to images; outputs named after the source file; the zip replaces its loose pages and a failed run leaves nothing behind; PDFBox works from a scratch file; recent PDFs go straight to a tool with a long press; required form fields are marked and checked.
- Capture: capped capture size with faster shutter; flash off, auto, on and torch; a spirit-level bubble; a strip of the pages taken so far in batch mode; volume-button shutter; auto-capture needs a real page in view and judges sharpness on the page; any camera is used when there is no back camera; the permission panel re-checks on resume and leads to Settings after "don't ask again"; several PDFs can be shared in at once; a detector that fails to load is reported.
- Documents: Files remembers its sort and layout; selection has Select all, Move to folder and favourites and survives rotation; folders can be removed and a rename that would merge two folders asks first; renames are capped and a name clash is pointed out; the recycle bin keeps documents for 7, 14 or 30 days and has Restore all; new scans are read in the language chosen in Settings; re-running OCR asks before replacing corrected text and says how many pages it could read; long tasks can be stopped from the screen; PDF settings rebuild on the next export instead of blocking per tap; added pages are auto-cropped and a failed batch leaves no files; Split all asks first and leaves nothing half done.

## Third round from the full review
- PDF tools: the page organiser has Undo, Reset, Rotate all, tick all and long-press to move a page to the start or end; its cards, the progress overlay and the result dialog read properly with a screen reader; share goes straight to the share sheet; Unlock, Protect, Extract and form messages say what really happened; radio options show readable labels; clearing recents asks first.
- Scanner: rotation in review is lossless (written to the photo's EXIF tag); frames are not analysed in No-detect mode or during review; the analyser reuses its buffers; file work leaves the main thread; HEIC on Android 8 and older is explained; review thumbnails are cached; device-to-device transfer carries the library while cloud backup stays off.
- Documents: the crop editor's page arrows meet the 48 dp target and page thumbnails announce their number and selection; the viewer caches rendered pages and reads the text once per file; the recycle bin updates once a minute; "Find answer" runs off the main thread.
