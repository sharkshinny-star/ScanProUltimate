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
