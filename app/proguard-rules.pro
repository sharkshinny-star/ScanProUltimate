# ML Kit and AndroidX libraries ship consumer rules. Keep the app's model names for safer
# crash reports and database migrations while still shrinking release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PDFBox-Android optionally references a JPEG2000 decoder that is not bundled; without these R8
# fails the release build with "Missing class com.gemalto.jp2...".
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-dontwarn org.bouncycastle.**

# OpenCV's Java classes are looked up from native code; keep them intact when minifying.
-keep class org.opencv.** {
    native <methods>;
    <fields>;
    <methods>;
}
-dontwarn org.opencv.**

# PDFBox-Android resolves encryption handlers, filters and font data by name at runtime. Without this the
# password-protect tool works in debug builds but fails once R8 has renamed or removed those classes.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ML Kit creates its components (text recognition, barcode, translate, language ID, common) by reflection
# through each registrar's no-argument constructor. R8 removes those constructors, ML Kit then starts with
# no components and every OCR, translation and QR call fails in release builds while debug builds work.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
