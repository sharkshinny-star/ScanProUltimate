# Third-party dependency notes

This file is an engineering checklist, not a substitute for legal review. Before distribution, generate a resolved dependency/license report from the actual release build and include all notices required by those resolved artifacts.

## Major components intentionally used

| Component                                                        | Project use                                                                 | License / distribution note                                           |
| ---------------------------------------------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| AndroidX / Jetpack                                               | Compose, CameraX, Room, WorkManager, Navigation, DataStore, Biometric, Exif | Android Open Source / Apache-2.0 family; verify resolved modules      |
| Jetpack Compose / Material 3                                     | Native UI                                                                   | Android Open Source / Apache-2.0 family                               |
| Kotlin / kotlinx.coroutines                                      | Language/runtime/concurrency                                                | Apache-2.0                                                            |
| Dagger / Hilt                                                    | Dependency injection                                                        | Apache-2.0                                                            |
| OpenCV 4.14.0                                                    | Document detection, perspective and enhancement                             | Apache-2.0 for current OpenCV releases; retain required notices       |
| Google ML Kit                                                    | OCR, translation, language identification, scanner fallback                 | Google SDK terms apply; review current terms before release           |
| Google Play services ML Kit document scanner                     | Compatibility scanner fallback                                              | Google Play services / SDK terms apply                                |
| PDFBox Android 2.0.27.0                                          | PDF merge/split/security operations                                         | Apache-2.0                                                            |
| ZXing core 3.5.3                                                 | QR code generator (encoding only; reading uses ML Kit)                      | Apache-2.0                                                            |
| Manrope (variable font, `app/src/main/res/font/manrope.ttf`)     | App typeface                                                                | SIL Open Font License 1.1; copyright 2018 The Manrope Project Authors |
| Google Play In-App Review (`com.google.android.play:review-ktx`) | Asks Play for the in-app review card once                                   | Play Core Software Development Kit Terms of Service                   |

## Product/IP rule

This repository is original implementation code. Do **not** copy CamScanner source code, reverse-engineered proprietary internals, icons, screenshots, marketing copy, trademarks, or a confusingly similar visual identity into the product.

## Release checklist

Before publishing:

1. Generate the final Gradle dependency graph from the signed release configuration.
2. Verify transitive licenses, not only direct dependencies.
3. Include any required `NOTICE`/attribution text in the app or distribution package.
4. Re-check Google ML Kit / Play services terms and privacy disclosures for the exact features enabled.
5. Keep your own brand, store listing, screenshots and design assets original.
