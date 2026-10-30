plugins {
    id("com.android.dynamic-feature")
}

// On-demand feature: ML Kit Translate alone adds about 16 MB of native code per CPU type, and most people
// never translate a scan. Google Play delivers this module the first time someone taps Translate (see
// TranslationFeature in the app module); it is fused into the universal APK bundletool builds.
android {
    namespace = "com.example.scanpro.translate"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":app"))
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
