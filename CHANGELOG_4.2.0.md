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
