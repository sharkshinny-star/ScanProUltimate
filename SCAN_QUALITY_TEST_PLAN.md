# Scan Quality & Performance Test Plan

This plan converts "premium scanner quality" into measurable release gates. Do not mark a gate passed until it is measured on physical Android hardware.

## Device matrix

Minimum suggested matrix:

| Class | Example characteristics | Why |
|---|---|---|
| Low/mid | Android 10–12, 4 GB RAM, modest ISP/CPU | Memory/latency stress |
| Mainstream | Android 13–15, 6–8 GB RAM | Typical user experience |
| Flagship | Android 15–16, 8+ GB, OIS, 90/120 Hz | Best-quality/high-refresh behavior |
| Tablet/foldable | Large/adaptive window | Layout/editor usability |

Record exact device model, Android version, camera resolution, and build SHA for every run.

## Corpus

Create a private test corpus with at least 100 captures across:

- Clean white A4/Letter pages
- Receipts: short/long/curled/faded
- Handwriting and notebooks
- Whiteboards
- ID-sized synthetic test cards
- Two-page book spreads
- Colored/patterned surfaces
- Strong perspective angles
- Low light and hard shadows
- Glare/reflections
- Motion blur/out-of-focus captures
- Dense small text, tables, mixed fonts
- Supported OCR scripts: Latin, Devanagari, Chinese, Japanese, Korean

Use only documents you have rights to test with. Do not include real sensitive IDs in a shared test corpus.

## Edge detection metrics

For a labelled subset, annotate true page corners and compute:

- Detection success rate: valid quadrilateral found when a document is visible.
- False-positive rate: quadrilateral produced when no document is present.
- Mean corner error normalized by image diagonal.
- Perspective-corrected border retention: page content is not clipped.

Suggested release targets for ordinary high-contrast pages under good light:

- >= 95% valid document detection before manual correction.
- Median normalized corner error <= 1.5% of image diagonal.
- < 2% false auto-captures in no-document/static-background trials.

These are product targets, not claims that the current unprofiled build already achieves them.

## Auto-capture behavior

Measure 20 attempts per device:

- Time from stable document to auto-capture.
- Number of captures while document is still moving.
- Repeated/double captures.
- Cancellation when blur/lighting falls outside acceptable range.

Suggested target: median stable-to-capture <= 1.2 s, with zero repeated captures in the 20-attempt scripted test.

## Final image quality

Compare Original / Auto / Clean / B&W / Grayscale / Color / Whiteboard on the same source image.

Inspect:

- Character-edge sharpness
- Haloing from sharpening
- Shadow flattening without text loss
- White-balance neutrality
- Color preservation for stamps/highlighters
- B&W threshold continuity on faint text
- Compression artifacts at each PDF quality tier

Keep the original source so destructive regressions can be reproduced.

## OCR accuracy

For printed documents with known ground truth, calculate character error rate (CER) or word error rate (WER). Split results by script and capture condition.

Suggested internal target under clean, well-lit, printed Latin text: median WER <= 5%. Treat handwriting separately; do not promise parity with printed-text OCR.

Also verify:

- OCR survives rotate/crop/filter changes after re-recognition.
- Manually corrected OCR is searchable.
- Searchable PDF text can be searched/copied externally.
- Unsupported/poor text returns an error/empty state rather than fabricated content.

## Performance gates

Measure using release/debug-profile builds as appropriate, not intuition.

Suggested targets on the mainstream test device:

- Camera preview available: <= 1.5 s after entering scanner.
- Analysis frame processing: p95 <= 100 ms at analysis resolution.
- Manual shutter feedback: <= 150 ms visual response.
- Full-resolution crop/enhance of a normal page: median <= 1.5 s.
- OCR of normal printed page: median <= 2.5 s after model ready.
- 10-page balanced PDF export: <= 10 s.
- No ANR during 100-page capture/edit/export stress test.

Exact performance varies by hardware; publish no benchmark claim without measured evidence.

## Memory/stability

Run:

1. Capture 100 pages.
2. Apply filters/crops repeatedly to 20 pages.
3. OCR all pages.
4. Export High/Balanced/Small PDFs.
5. Reorder pages and export again.
6. Background/foreground the app repeatedly.
7. Rotate the device during allowed flows.

Watch Java/Kotlin heap, native OpenCV memory, bitmap allocations, and file-descriptor counts. Release Mat/bitmap resources promptly and fail gracefully on low storage.

## Accessibility/editor precision

- Crop handles remain targetable at large font/display sizes.
- Loupe accurately reflects the dragged corner area.
- TalkBack announces scanner controls and document actions.
- Contrast remains readable in light/dark/dynamic-color themes.
- Critical actions remain reachable at 200% font scaling.

## Regression record

For every release, preserve:

- App version + git commit/SHA
- Device matrix
- Corpus version
- Metric summary
- Known regressions
- Representative input/output pairs
- Crash/ANR report

A premium scanner should improve against this baseline release over release rather than relying on subjective visual impressions alone.
