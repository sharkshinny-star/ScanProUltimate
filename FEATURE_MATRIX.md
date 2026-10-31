# Feature matrix

| Area                                   | Status                          | Implementation                                               |
| -------------------------------------- | ------------------------------- | ------------------------------------------------------------ |
| CameraX preview                        | Implemented                     | Native CameraX                                               |
| Live edge detection                    | Implemented                     | OpenCV greyscale contour/quadrilateral pipeline              |
| Auto capture                           | Implemented                     | Corner stability + quality gate                              |
| Manual capture                         | Implemented                     | CameraX ImageCapture                                         |
| Flash / zoom / focus / exposure        | Implemented                     | CameraControl                                                |
| Low-light / blur guidance              | Implemented                     | mean brightness + Laplacian variance                         |
| Gallery import                         | Implemented                     | Android Photo Picker                                         |
| Multi-page / batch                     | Implemented                     | Scanner session list                                         |
| Perspective correction                 | Implemented                     | full-res second-pass + matrix transform                      |
| Manual four-corner crop                | Implemented                     | loupe + undo/redo                                            |
| Non-destructive edits                  | Implemented                     | retained source + edit metadata                              |
| Document filters                       | Implemented                     | OpenCV + adaptive thresholding                               |
| Manual brightness/contrast/saturation  | Implemented                     | low-res live preview + full-res commit                       |
| PDF creation                           | Implemented                     | Android PdfDocument                                          |
| Searchable PDFs                        | Implemented                     | OCR page text layer; word-level geometry is a hardening item |
| PDF import                             | Implemented                     | PdfRenderer -> editable page workspace                       |
| Merge / split / page extract           | Implemented                     | Repository page operations                                   |
| Compression presets                    | Implemented                     | max dimension + JPEG quality controls                        |
| A4 / Letter / Auto                     | Implemented                     | PDF layout controls                                          |
| Watermark                              | Implemented                     | non-destructive annotation pipeline                          |
| Signature                              | Implemented                     | drawn signature overlay                                      |
| Text / highlight / redaction           | Implemented                     | normalised page annotations                                  |
| Password-protected PDF                 | Implemented                     | PDFBox AES-256 policy                                        |
| Print                                  | Implemented                     | Android Print Framework                                      |
| JPG / long image / ID copy             | Implemented                     | special exporters                                            |
| OCR                                    | Implemented                     | bundled Latin/Devanagari/Chinese/Japanese/Korean models      |
| Editable OCR                           | Implemented                     | page OCR text update + FTS refresh                           |
| Full-text search                       | Implemented                     | Room FTS4                                                    |
| TXT export                             | Implemented                     | local export file                                            |
| Translation                            | Implemented                     | ML Kit; model may need initial download                      |
| Local document classification          | Implemented                     | source-grounded rules                                        |
| Fact extraction                        | Implemented                     | exact OCR substrings                                         |
| Extractive summary                     | Implemented                     | offline/source-grounded                                      |
| Action-item suggestions                | Implemented                     | source lines containing action markers                       |
| Q&A                                    | Implemented                     | extracts best matching OCR passages, no generative invention |
| Folders / rename folders               | Implemented                     | Room metadata                                                |
| Favourites / tags                       | Implemented                     | Room metadata                                                |
| Grid / list / sort                     | Implemented                     | Compose library                                              |
| Multi-select / bulk share/delete       | Implemented                     | Compose library                                              |
| Trash / restore / cleanup              | Implemented                     | Room + WorkManager                                           |
| Biometric app gate                     | Implemented                     | AndroidX Biometric                                           |
| Encrypted portable backup              | Implemented                     | AES-GCM `.scanpro` package                                   |
| Forced login                           | Not used                        | Core app is account-free                                     |
| Automatic cloud sync                   | Requires backend                | Intentionally not faked                                      |
| Collaboration                          | Requires backend                | Intentionally not faked                                      |
| Play subscription entitlement          | Requires Play product IDs       | Intentionally not faked                                      |
| Server generative AI                   | Requires secure backend/consent | Intentionally not faked                                      |
| DOCX/XLSX cloud conversion             | Requires service/library choice | Not faked                                                    |
| APK/AAB validation in this environment | Not available                   | Android SDK/Gradle/hardware unavailable                      |
