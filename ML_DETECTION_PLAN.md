# Plan: reaching Google / Adobe scan quality (page and book detection, image quality)

Status: plan only (2026-11-24). Nothing here is built. Owner request: train our own model, like Google's, so the
scanner starts detection only when a page or book is really in view, and the picture quality matches Adobe Scan,
iLovePDF and Google PDF Scanner.

## 1. What those apps do that we do not (yet)

- **Detection.** Google's Document Scanner, Adobe Scan and similar apps use a small trained neural network, not only edge
  finding. It looks at the whole frame and answers two questions: is a document here, and where are its four corners.
  Our detector (`DocumentEdgeDetector`, OpenCV edges and lines) is strong on clean scenes (see `scripts/scan-bench`) but
  cannot tell "a page" from "a laptop, keyboard or notebook cover" (F284: 21 to 34 % of the frame still gets cropped),
  and it misses pages on low-contrast desks. A trained model fixes exactly those two weaknesses.
- **Image quality.** Mostly not detection: a full-resolution still (not the preview), accurate corners, perspective
  correction, shadow and uneven-light removal, sharp text, and no over-processing. We have most of the pieces
  (`MatFilters`, `enhance` bench); the missing parts are accurate corners and a measured target.
- ML Kit's own model cannot be retrained or extracted. We train **our own** small model, and use ML Kit's Document
  Scanner (already in the app as a fallback) only as a comparison on a phone.

## 2. Target numbers (set before training, so we know when we are done)

| Measure | Today (bench) | Target |
|---|---|---|
| Page found when a page is in view | 50/50 synthetic | at least 98 % on real photos |
| False outline when no page is in view | 6/12 clean (was 7) | at most 2 % of frames |
| Corner error (percent of the longer side) | not measured on real photos | median under 0.5 %, 95th percentile under 2 % |
| Outline overlap (IoU) with a hand-marked page | not measured | median at least 0.98 |
| Books (two-page spread) | partly | found and split line within 1 % |
| Time per live frame | classical, a few ms | model at most 15 ms on a mid-range phone, 8 fps or better |
| App size | n/a | model under 3 MB (int8) |

## 3. Work in order

### Phase 0: build the test set first (1 to 2 weeks, no training yet)

Without real photos any number is a guess.

1. **Collect** 1,500 to 3,000 photos with the owner's own phone(s): pages, books open flat and curved, receipts, ID cards,
   whiteboards, on 10 or more desks and floors, light and dark paper, shadows, a hand holding the page, and **negatives**
   (desk, laptop, keyboard, phone, mug, notebook cover, empty table). Include mid-range and low-end phones and a front
   window light at night. Offline-first rule: photos stay on the owner's machine; no upload to any service.
2. **Label**: four corners per page, a page/book/ID/none class, and the book's split line. Use an offline tool
   (CVAT or Label Studio, run locally). About 3 minutes a photo with corner snapping; budget two or three people-days.
3. **Public data** to add volume (check each licence before use): SmartDoc and MIDV-500/2019 (cards and pages in video),
   DocUNet (warped pages), a document-dewarping set. Treat as extra training only; the owner's own photos are the
   test set.
4. **Synthetic data**: extend the scene generator in `scripts/scan-bench` (pages pasted onto desks with perspective,
   shadow, blur, noise, JPEG, colour cast) to produce 50,000 labelled images cheaply. It already knows the true corners.
5. **Scorer**: extend `scan-bench` so it scores the classical detector on the real labelled set with the table above.
   That gives the baseline to beat.

### Phase 1: train the model (1 to 2 weeks)

- **Needs a GPU**: this cloud container has 4 CPUs, no GPU and no PyTorch, so training runs on the owner's machine,
  Google Colab or a rented GPU (a few US dollars of time for a model this small). Cloud sessions can still prepare data,
  write the training script and score results.
- **Model**: a light mobile network (MobileNetV3-Small or EfficientNet-Lite0 backbone) with a small decoder, input
  256 x 256 (keep aspect with padding). Outputs: (a) a page mask, (b) four corner heatmaps (sub-pixel by soft-argmax),
  (c) a presence class: page, book, ID card, none.
- **Loss**: mask (Dice plus cross-entropy), corners (heatmap focal loss plus L1 on the soft-argmax), class
  (cross-entropy). Heavy hard-negative weighting for the "none" class, because false outlines are the owner's main complaint.
- **Augmentation**: perspective, rotation 0 to 360, scale (page small in frame), blur, motion blur, glare, shadow,
  colour cast, JPEG, hand and finger occluders, desk textures.
- **Export** to TFLite (or ONNX Runtime) with int8 quantisation, keeping accuracy within 0.5 % of float.
  Dependencies must be Apache or MIT licensed, and the model and its licence go in `app/src/main/assets` and
  `licences` (offline, nothing downloaded at run time).

### Phase 2: put it in the app, behind a switch (1 week)

- A new `scanner/NeuralPageFinder` runs on the analyser frame at 6 to 10 fps (not every frame).
- **How it combines with the classical detector** (this is the safe design, and what the strong apps effectively do):
  1. The model decides **whether** to start detection (presence class and confidence, held for about 3 frames:
     `GuidanceHold` and `OutlineTracker` already do the holding).
  2. The model's mask is a **region of interest**; `DocumentEdgeDetector` and `LineQuadFinder` then find the exact
     straight edges inside it for sub-pixel corners. The model's own corners are used if the classical step fails.
  3. If the model file is missing or slow on a device, fall back to today's behaviour, unchanged.
- The same model also replaces the size-based gate (`MIN_PAGE_AREA`, F284) for imports and Organise pages.
- Ship as a setting ("Smart page detection", on by default once the gates are met); keep a one-tap off.

### Phase 3: picture quality (can start now, in parallel)

1. Capture the full-resolution still, with auto-exposure and focus lock on the page area once detected (CameraX
   metering rectangles from the corners).
2. Corners from Phase 2 give a better warp; keep the aspect-ratio step but measure it on real photos (F285 needs
   device photos of an A4 sheet at about 40 degrees).
3. Shadow and uneven-light removal and sharpening stay classical and are scored by `scan-bench enhance`; add real
   photos from Phase 0 as inputs and compare side by side with Adobe Scan, Google PDF Scanner and iLovePDF **on the
   same photographed pages** (owner's eyes plus OCR word accuracy as a number).
4. Optional later: a small learned de-warp for curved book pages (DocUNet style), only if flat-page results are
   already at target.

### Phase 4: prove it and ship (1 week)

- Run the Phase 0 scorer on the real test set: classical vs model vs combined vs ML Kit Document Scanner.
- Ship only if every row of the table in section 2 is met on a release build on at least two phones; record it in
  `BUILD_AND_TEST.md` and `AUDIT_FINDINGS.md`.
- Add a regression test that loads the model and checks a handful of known photos (kept small).

## 4. What is needed from the owner

1. Say who photographs the test set and how many phones (at least two, one low-end).
2. A place to train: owner's computer with a GPU, Colab or a rented GPU.
3. Agree that the training photos stay private and are not committed to the repository (only the trained model and a
   few small test images are).
4. Confirm the licence rule: only Apache/MIT/BSD code and data allowed to be used commercially.

## 5. Honest limits

- A cloud session cannot train (no GPU) or run the Android app, so results there come from the desktop benchmark and
  must be confirmed on a phone (R55 style).
- Matching Google's accuracy depends mostly on **data quantity and variety**, not on a cleverer network. Expect the
  first model to beat today's detector on false outlines quickly, and to take several rounds of adding hard photos to
  reach the best scores.
- None of the targets above are promised; they are the thresholds to ship at.
