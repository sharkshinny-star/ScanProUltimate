# Scanner UI and quality validation contribution

## Scope

This patch targets the current ScanPro camera/review flow without changing the crop or PDF export pipeline:

- The live scanner displays three compact indicators: **Sharp/Blurry**, **No glare/Glare**, and **Aligned/Tilted**. They appear only when a page boundary is detected; labels are not announced repeatedly to TalkBack.
- When a confident page appears rolled by roughly 12 degrees or more, the debounced guidance asks the user to straighten it. Manual and automatic capture remain available: skew can often be corrected in review.
- Captured pages get a **tilted-page** advisory in the existing quality/Retake notice.
- Glare detection measures the bright patch **inside the detected quadrilateral**, rather than inside its axis-aligned bounding box, reducing false warnings from white backgrounds.
- Repeated native `MatOfPoint` allocations in the live detector are released after use.

## Constraints and interpretation

The four outline corners reveal geometric skew, **not** whether printed text is rotated 90/180 degrees. Correct text orientation would require an OCR-based classifier and explicit user review. Strong perspective distortion can make roll ambiguous; the new check deliberately returns `null` instead of guessing. Glare can be confused with genuinely white page regions, and Laplacian blur thresholds depend on device/sensor: warnings are advisory rather than evidence of unreadable text.

## Verification checklist on real hardware

1. With a page straight, all three indicators should show positive feedback; with a 15–25 degree in-plane tilt, **Tilted** should appear without flickering each frame.
2. Place a dark page on a bright white desk, framing corners within the desk. Verify that the desk is not incorrectly classified as glare, then add a concentrated reflection **on the page** and verify that glare is flagged.
3. Move the camera while capturing, then retake a clearly out-of-focus page. Check blur flags at 0.5x, 1x, and 2x zoom, in daylight and dim light.
4. Confirm that automatic capture remains functional at a moderate angle and that the review notice offers **Retake**. Check rotation and crop editing separately, including EXIF-rotated images.
5. Check portrait, landscape, 200% font size, TalkBack, and devices without an accelerometer. The status indicators must not cover shutter or other camera controls.
6. Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:compileDebugKotlin`; ideally include camera instrumentation and real-photo golden fixtures before merge.

Thresholds should be tuned with labelled images taken across multiple phone models before claiming production accuracy.
