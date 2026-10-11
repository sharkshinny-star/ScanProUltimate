# Research notes — October 2026

## Competitive baseline

CamScanner's current Google Play listing emphasizes the same broad categories a competitive scanner needs to cover: high-quality scan/enhancement, OCR, PDF/document editing, platform sync, secured sharing, ID mode, book mode, e-signature and collaboration/cloud storage.

Public Google Play reviews from 2026 still praise the core scanning capability, while repeated negative reviews focus on advertising volume, subscription friction, and features moving behind a paid tier. That informed a product principle for ScanPro: **core scanning should remain fast, direct and privacy-first; monetization should never interrupt an urgent scan flow.**

Sources consulted:

- CamScanner Google Play listing: https://play.google.com/store/apps/details?id=com.intsig.camscanner
- CameraX image-analysis guidance: https://developer.android.com/media/camera/camerax/analyze
- ML Kit Text Recognition Android guide: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878

## Architecture decision: CameraX/OpenCV first

Google's document-scanner API is useful, but making it the only scanner would prevent full ownership of:

- live boundary UI
- stability thresholds
- auto-capture behavior
- blur/lighting guidance
- exposure/focus UX
- fallback behavior
- scan-quality experimentation

Therefore ScanPro uses CameraX + OpenCV as the primary path and retains the Google scanner as a compatibility fallback.

## Privacy decision

The local workflow does not need document uploads. OCR is configured with bundled ML Kit text models. Search, classification, fact extraction, summaries and Q&A operate on local OCR text. Translation may download a model, but translation execution is local after the model is available.

Cloud sync or generative AI should only be introduced through explicit backend interfaces and consent after a real privacy/security design is chosen.
