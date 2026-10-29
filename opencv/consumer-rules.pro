# OpenCV's Java classes are reached from native code by name; keep them whole (same rules as the Maven artifact).
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
