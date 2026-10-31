plugins {
    id("com.android.library")
}

// OpenCV 4.14.0 trimmed to the modules the app uses (core, imgproc, photo and the Java bindings).
// The Java sources and native libraries here are produced by scripts/opencv/build-opencv-android.sh from
// unmodified OpenCV sources; see scripts/opencv/README.md. The Maven artifact org.opencv:opencv ships every
// module and is about five times larger per CPU type.
android {
    namespace = "org.opencv"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Generated bindings: not code the app's lint rules are meant for. opencv/lint.xml silences every check here.
        lintConfig = file("lint.xml")
        abortOnError = false
        checkDependencies = false
    }
}
