# Privacy Policy Template — ScanPro Ultimate

> **The publishable page is `docs/privacy-policy/index.html`** (finished text; only the owner's details are left to fill). This file is the older template; if you change one, change the other.
>
> **Template only.** Replace bracketed fields and have the final policy reviewed for the countries where you distribute the app. The published policy must describe the exact production build, including any analytics, ads, billing, sync, account, or cloud-AI services you later add.

**Effective date:** [DATE]  
**Developer:** [LEGAL NAME]  
**Contact:** [EMAIL]  
**Website:** [HTTPS URL]

## 1. Overview

ScanPro Ultimate is designed as an offline-first document scanner. By default, documents, page images, OCR text, document metadata, annotations, and generated files are stored locally in the app's private storage on your Android device. The app does not require an account for its core scanning workflow.

## 2. Camera and imported files

The app requests camera access when you choose to scan with the custom CameraX scanner. Camera frames are used to detect document boundaries, estimate blur/lighting, and capture pages. The app can also import images through Android's Photo Picker and files through the Storage Access Framework. Core scanning does not require broad device-storage access.

Imported files and captured pages are processed for features such as cropping, perspective correction, enhancement, OCR, PDF creation, and document search.

## 3. On-device OCR and document features

Supported OCR is performed on the device using Google ML Kit text-recognition components. Recognised text can be stored locally so that you can search documents, edit OCR text, export text, and create searchable PDFs.

Google's ML Kit components, which the app uses for text recognition, barcode scanning, language identification, translation and the document-scanner fallback, send Google diagnostic and usage information about how those components run. According to Google's ML Kit data disclosure this is: device information (manufacturer, model, operating system version), application information (package name and version), a per-installation identifier, performance metrics (such as how long a recognition took), the settings the component was run with, input and output sizes, the library version, event types and error codes, and, for translation, the chosen source and destination languages. Google states that it is sent over HTTPS, is used for diagnostics and usage analytics, and is not passed to third parties. It does not include your images, scans or recognised text, and it cannot be switched off inside the app. This is what the app declares in its Google Play Data safety form (Diagnostics, Other app performance data, and Device or other IDs: collected, required, for analytics). Keep this paragraph and that form consistent, and re-check Google's disclosure when the ML Kit libraries are updated.

The app also includes deterministic offline document-insight features that can classify common document types and extract or summarise information already present in recognised text. These offline features are not a substitute for professional, legal, financial, or medical review and may contain recognition/extraction errors.

## 4. Translation models

If you use the optional translation feature, Google ML Kit may download the language model needed for translation. Translation then runs on the device. Network/model-download behaviour is governed in part by the platform/library provider. Do not state that the application is completely network-free if this feature or other online dependencies remain enabled in the shipping build.

## 5. Google document-scanner fallback

The app includes an optional Google ML Kit / Google Play services document-scanner fallback on compatible devices. This is intended as a compatibility option if the custom scanner cannot be used. Review Google's current documentation and privacy disclosures before release and describe provider processing accurately in the final policy.

## 6. Local document storage

The application stores document data in private application storage and indexes metadata/OCR text locally for search. Documents remain available without an internet connection unless you remove the app, clear its data, or delete the documents.

The app's biometric lock is an access-control convenience. It should not be described as full at-rest encryption of the entire live document library unless you later add and verify such encryption.

## 7. Encrypted portable backups

The app can export a password-protected `.scanpro` backup package. The package is encrypted using AES-GCM with a password-derived key. The password is not recoverable by the app developer. If you forget the password, the backup may be unrecoverable.

This encrypted export is separate from the app's live local library.

## 8. PDF password protection

When requested, the app can create a password-protected PDF. Security depends on the chosen password and the PDF implementation/viewer. Users should choose a strong, unique password and transfer sensitive files carefully.

## 9. Sharing, printing, and saving copies

When you choose Share, Print, or Save a copy, the app provides the selected file to Android or to the destination app/service you choose. Once a file leaves ScanPro Ultimate, the receiving app/service's privacy practices apply.

## 10. Trash and deletion

Deleted documents can be moved to a local trash area so they can be restored. The app can permanently purge items after the configured retention period (currently designed around 30 days) or when you explicitly empty trash/permanently delete an item. Device backups, files you manually exported, or copies held by other apps are outside this local deletion process.

## 11. Data we do not intentionally require for the core app

The provided source does not require precise location, contacts, microphone, call logs, SMS, or broad all-files access for its core workflow. It does not include an advertising SDK, account system, or cloud AI backend in this source package.

If you add analytics, crash reporting, advertising, billing, cloud storage, accounts, collaboration, or server-based AI, update this policy and the Play Data Safety declaration before shipping that build.

## 12. Security

The app uses Android private storage, scoped file-access patterns, temporary content-URI grants, optional biometric access control, and password-encrypted portable exports. No software can guarantee absolute security. Keep Android updated and protect your device with a strong lock screen.

## 13. Children's privacy

[STATE WHETHER THE APP IS DIRECTED TO CHILDREN. IF NOT, DESCRIBE YOUR AGE POLICY CONSISTENTLY WITH YOUR PLAY STORE TARGET-AUDIENCE DECLARATION.]

## 14. Changes to this policy

We may update this policy when features or legal requirements change. The current version and effective date will be published at [POLICY URL].

## 15. Contact

For privacy questions or deletion/support requests, contact:

[LEGAL NAME]  
[EMAIL]  
[POSTAL ADDRESS, IF REQUIRED]
