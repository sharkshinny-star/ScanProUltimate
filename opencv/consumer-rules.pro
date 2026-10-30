# The JNI side looks up these by name: the core types it builds and returns (Mat, Point, Rect, Scalar, the
# MatOf* classes, CvException), the converters, and every native method's declaring class. Imgproc and
# Photo themselves are only reached from app code, so their unused functions can be removed.
-keep class org.opencv.core.** { *; }
-keep class org.opencv.utils.** { *; }
-keep class org.opencv.android.** { *; }
-keep class org.opencv.osgi.** { *; }
-keepclasseswithmembers class org.opencv.** { native <methods>; }
-keepclassmembers class org.opencv.** { long nativeObj; }
-dontwarn org.opencv.**
