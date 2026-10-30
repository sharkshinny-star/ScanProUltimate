# ML Kit and AndroidX libraries ship consumer rules. Keep the app's model names for safer
# crash reports and database migrations while still shrinking release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PDFBox-Android optionally references a JPEG2000 decoder that is not bundled; without these R8
# fails the release build with "Missing class com.gemalto.jp2...".
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-dontwarn org.bouncycastle.**

# OpenCV's own rules ship with the :opencv module (opencv/consumer-rules.pro).
-dontwarn org.opencv.**

# PDFBox-Android reaches a few classes by name; the rest is referenced statically and can be shrunk:
#  - SecurityHandlerFactory instantiates the encryption handlers through their constructors
#  - FDFAnnotation builds its subclasses from the annotation subtype name
#  - filters and font loading are looked up through registries of class names
#  - JPXFilter loads an optional JPEG 2000 decoder (absent here, hence the dontwarn above)
-keep class com.tom_roush.pdfbox.pdmodel.encryption.** { *; }
-keep class com.tom_roush.pdfbox.pdmodel.fdf.** { *; }
-keep class com.tom_roush.pdfbox.filter.** { *; }
-keep class com.tom_roush.pdfbox.pdmodel.font.** { *; }
#  - PDNumberTreeNode creates its value objects through their constructors: page labels (PDPageLabelRange)
#    and the tagged-PDF parent tree (PDParentTreeValue). Only reached when a PDF carries those structures.
-keep class com.tom_roush.pdfbox.pdmodel.common.PDPageLabelRange { <init>(...); }
-keep class com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDParentTreeValue { <init>(...); }
-dontwarn com.tom_roush.pdfbox.**

# ML Kit creates its components (text recognition, barcode, translate, language ID, common) by reflection
# through each registrar's no-argument constructor. R8 removes those constructors, ML Kit then starts with
# no components and every OCR, translation and QR call fails in release builds while debug builds work.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# The on-demand translate module's entry point is created by name from TranslationFeature.
-keep class com.example.scanpro.translate.MlKitTranslator { <init>(); }
