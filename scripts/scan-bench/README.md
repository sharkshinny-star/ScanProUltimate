# Scan detection benchmark (desktop)

Measures `DocumentEdgeDetector` on generated scenes without a phone or the Android SDK: the app's detector sources are
compiled unchanged against small stubs (`stubs/`) and desktop OpenCV (`org.openpnp:opencv`). Use it in cloud sessions;
the device benchmark (`ScanDetectionBenchmarkTest`, see `BUILD_AND_TEST.md`) remains the reference.

```bash
cd scripts/scan-bench && gradle -q run --args="extra"
```

Other arguments: `only=shadow`, `dump=/some/dir` (images with true and found outlines), `time` (median detection time).
Needs a JDK 17+ and Gradle 8; the first run downloads about 100 MB of OpenCV.
