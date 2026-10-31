plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

// Drives the release app on a connected phone or emulator to record which code runs at startup and on the
// main screens. Run: ./gradlew :app:generateReleaseBaselineProfile  (output is merged into the app build).
android {
    namespace = "com.example.scanpro.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
