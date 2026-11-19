# Scan detection benchmark (desktop)

Measures `DocumentEdgeDetector` on generated scenes without a phone or the Android SDK: the app's detector sources are
compiled unchanged against small stubs (`stubs/`) and desktop OpenCV (`org.openpnp:opencv`). Use it in cloud sessions;
the device benchmark (`ScanDetectionBenchmarkTest`, see `BUILD_AND_TEST.md`) remains the reference.

```bash
cd scripts/scan-bench && gradle -q run --args="extra"
```

`extra` also adds colour cases (`c_`: coloured paper, a colour photo on the page, a warm lamp, coloured desks) and desk clutter with no page (`n_`: laptop, keyboard, phone, mug, notebook cover, silver laptop on a dark desk, tablet showing a page), and pages with a printed dark frame (`c_dark_border_*`). Every row also prints `framed`, the outline's framed sides (`PageFraming`, F177). `gradle -q run --args="extra colour"` also runs every scene with its colour map, the way the app does for a captured photo (the live camera has none), and prints that result on a second line with its own summary.

`extra` also adds ID cards (`k_`, 2026-11-19): a tinted card with rounded corners, a coloured header band, a portrait and a chip, on four desks, far away, tilted, filling ID card mode's guide, under a shadow and a warm lamp; with `colour` the detector also gets the Cb and Cr planes (colour edges, F213), as the app passes them.

`gradle -q run --args="extra jitter"` measures frame-to-frame steadiness instead (F213): 24 noisy frames of each scene, counting missing outlines, jumps and trembling for the detector's own outlines and for what `OutlineTracker` draws from them.

Other arguments: `live` (with `colour`: the colour map at a quarter size, as the live camera makes it), `only=shadow`, `dump=/some/dir` (images with true and found outlines), `time` (median detection time).

`gradle -q run --args="enhance"` scores the page filters (`MatFilters.kt`, compiled unchanged) instead: a known page
is degraded (warm cast, brightness ramp, corner shadow, dim noise, glare) and each filter's output is measured for
text contrast, background evenness, blown whites, halo around strokes, colour fidelity (Lab dE of known patches) and
retention of faint content (a pencil line and a highlighter bar). `dump=` writes each output image. OCR word accuracy
needs the phone and is not covered here.
Needs a JDK 17+ and Gradle 8; the first run downloads about 100 MB of OpenCV.
