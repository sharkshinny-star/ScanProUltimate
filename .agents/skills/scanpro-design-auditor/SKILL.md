---
name: scanpro-design-auditor
description: Audit and improve ScanPro's visual design system, Material 3 compliance, usability, accessibility, typography, spacing, touch targets, and visual polish across light and dark themes. Use when evaluating camera HUD controls, document lists, crop handles, PDF viewer, OCR/translation results, or empty/loading/error states.
---

# ScanPro Design Auditor Skill

This skill guides visual quality, usability, accessibility, and Material 3 design audits for **ScanPro Ultimate**, ensuring premium UI polish while preserving brand identity and offline performance.

## Target Evaluation Areas

When auditing or polishing ScanPro's UI/UX, systematically evaluate these 9 functional areas:

### 1. Camera HUD & Live-Scanning Controls (`scanner/CameraScannerScreen.kt`)
- Frosted glassmorphic top bar (`CameraGlass`), batch page counter pill, and clear mode tabs (`ModeTab`).
- Touch target sizing (minimum 48dp on all camera deck actions and `BottomPill` controls).
- Mode selection ergonomics: Document, ID Card (front/back composition), Book (two-page spread), OCR, Barcode.

### 2. Document Gallery, Folders & Search (`feature/library/LibraryScreen.kt`)
- `ScanHeroCard` padding and tip card layout breathing room above bottom navigation bar and raised FAB.
- Folder organization, tag chips, and sort controls (Date/Name/Size, Ascending/Descending).
- Search marquee behavior and recent search term history.

### 3. Edge Detection Feedback & Auto-Capture UX (`scanner/DocumentEdgeDetector.kt`)
- High-contrast animated quad overlay during live preview.
- Clear guidance messaging (Glare warning, Too Dark, Hold Steady, Same Page skip hint).
- Spine guide alignment for two-page Book mode.

### 4. Document Crop & Perspective Adjustments (`feature/document/CropEditorDialog.kt`, `ScanReviewScreen.kt`)
- Draggable normalized quad handles with magnified loupe preview.
- Quick crop actions: Auto-detect (`AutoFixHigh`), Full bounds reset (`No crop`), Curve curvature toggle (`Gesture`).
- Multi-tier downsampled rendering resilience for large high-res camera photos.

### 5. PDF Preview, Annotations & Tools (`feature/viewer/PdfViewerScreen.kt`, `feature/tools/PdfToolsScreen.kt`)
- Single-page image viewer & multi-page PDF viewer pinch-to-zoom performance (`graphicsLayer` matrix transforms).
- Non-destructive annotations (Text, Highlight, Redaction, Signature) rendering and layering.
- Tool sections (Edit, Convert, Secure, Image Tools) card hierarchy and localized labels.

### 6. OCR, Translation & Document Intelligence (`ScanSavedSheet.kt`, `FormFillDialog.kt`)
- Searchable PDF status badge and async OCR text completion updates.
- Smart action cards (Business card $\rightarrow$ Add to contacts, Receipt $\rightarrow$ Copy total).
- Extraction result formatting, text chunking, and language selection.

### 7. Material 3 Theming, Color Palettes & Contrast
- 5 Accent Palettes (`Palettes.kt`): Indigo, Ocean, Emerald, Sunset, Berry (Material Color Utilities fidelity schemes).
- Minimum 4.5:1 text contrast ratio for body copy and 3:1 for interactive UI components.
- Smooth crossfade transitions (`ThemeTransition`) across Light/Dark modes and palette updates.

### 8. Accessibility & Responsiveness
- Enforce minimum 48dp $\times$ 48dp touch targets across all clickable icons, chips, and buttons.
- TalkBack content descriptions on sliders, icons, and page thumbnails.
- Responsive scaling on phone screens, foldables, tablets, and split-screen windowing (Android 17 adaptability).

### 9. State Coverage (Loading, Empty, Offline, Error)
- Empty states with clear call-to-action buttons (`EmptyLibrary`).
- Offline fallback indicators for on-demand translation or dynamic feature modules.
- Non-intrusive localized error banners (`UserFacingException`).

---

## Audit Execution Principles

1. **Use Real Screenshots & Compose Previews**: Inspect designs visually via `render_compose_preview` or device screenshots.
2. **Preserve Brand Identity**: Maintain ScanPro's distinct visual identity (mint green scanning laser line, indigo-azure-teal brand gradient).
3. **Respect Android Guidelines**: Adhere to Material Design 3 specifications, WCAG 2.1 AA accessibility guidelines, and Android 17 windowing adaptability.
