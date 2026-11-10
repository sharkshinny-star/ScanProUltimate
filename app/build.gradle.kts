plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
}

// Two ways to build:
//  * Default (Google Play): an app bundle, with translation as an on-demand module that Play installs the
//    first time someone translates. `./gradlew :app:bundleRelease`
//  * -Pscanpro.sideload=true: standalone APKs to share directly, one per CPU type plus a universal one,
//    with translation compiled into the app. `./gradlew :app:assembleRelease -Pscanpro.sideload=true`
// Android does not allow on-demand modules together with per-CPU APK splits, hence the switch.
val sideload = providers.gradleProperty("scanpro.sideload").orNull == "true"

android {
    namespace = "com.example.scanpro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scanpro.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "4.1.0"

        vectorDrawables { useSupportLibrary = true }
        // True when the ML Kit models ship inside the APK (-Pscanpro.bundledMl=true): no Play-services download checks.
        buildConfigField("boolean", "BUNDLED_ML", (providers.gradleProperty("scanpro.bundledMl").orNull == "true").toString())
        // Links shown in Settings. Empty (the default) hides the rows: -Pscanpro.privacyPolicyUrl=... -Pscanpro.termsUrl=...
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${providers.gradleProperty("scanpro.privacyPolicyUrl").orNull.orEmpty()}\"")
        buildConfigField("String", "TERMS_URL", "\"${providers.gradleProperty("scanpro.termsUrl").orNull.orEmpty()}\"")
        // 32-bit x86 is only used by old emulators; leaving it out removes ~70 MB of native code from universal APKs.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key is never stored in the repository. Provide it per build, either as Gradle properties
    // (for example in ~/.gradle/gradle.properties) or as environment variables:
    //   SCANPRO_KEYSTORE (path), SCANPRO_KEYSTORE_PASSWORD, SCANPRO_KEY_ALIAS, SCANPRO_KEY_PASSWORD
    // Without them the release build still succeeds but is unsigned, so it cannot be installed or uploaded.
    fun signingValue(name: String): String? = providers.gradleProperty(name).orNull ?: System.getenv(name)
    signingConfigs {
        create("release") {
            val keystore = signingValue("SCANPRO_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = signingValue("SCANPRO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("SCANPRO_KEY_ALIAS")
                keyPassword = signingValue("SCANPRO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Shared APKs (sideload builds only): one small APK per CPU type plus a universal one. Play Store
    // bundles split by CPU on their own.
    splits {
        abi {
            isEnable = sideload
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    if (sideload) {
        // No Play to deliver modules: the translation code is built into the app instead.
        sourceSets.getByName("main").kotlin.srcDir("../translate/src/main/java")
    } else {
        // Delivered by Google Play when first used; see TranslationFeature and translate/build.gradle.kts.
        dynamicFeatures += setOf(":translate")
    }

    testOptions {
        unitTests.all {
            // -Pscanpro.updateGoldens=true rewrites the golden images of the imaging tests (see MatFiltersGoldenTest).
            it.systemProperty("scanpro.updateGoldens", (project.findProperty("scanpro.updateGoldens") ?: "false").toString())
            // -Pscanpro.photoDir=<folder of photos> enables RealPhotoEquivalenceTest.
            it.systemProperty("scanpro.photoDir", (project.findProperty("scanpro.photoDir") ?: "").toString())
        }
    }

    packaging {
        // The C++ runtime ships with the :opencv module (from the NDK); Google libraries bundle a copy too.
        jniLibs { pickFirsts += "**/libc++_shared.so" }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                // PDFBox brings BouncyCastle for PDF encryption; its post-quantum lookup tables (about 8 MB) are
                // never used. The patterns name the folders' contents: a bare "**/sike*" would only match a
                // path segment, not the files inside it, and shipped every table.
                "org/bouncycastle/pqc/crypto/sike/**",
                "org/bouncycastle/pqc/crypto/picnic/**"
            )
        }
    }
}

composeCompiler {
    // Classes from other modules the compiler can't see into; listing them lets it skip recompositions
    // when they are passed unchanged. Compose metrics: -Pscanpro.composeMetrics=true, then see build/compose_metrics.
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
    if (providers.gradleProperty("scanpro.composeMetrics").orNull == "true") {
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
        reportsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    // One launch-screen implementation for every Android version, with a dark variant and a custom exit animation.
    implementation("androidx.core:core-splashscreen:1.2.0")
    // Applies the libraries' bundled Compose baseline profiles on installs that bypass Play, so first runs are smoother.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Measured startup profile, recorded on a device by the :baselineprofile module.
    baselineProfile(project(":baselineprofile"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Debug builds only: reports leaked activities and bitmaps in a notification. Not shipped in release.
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // CameraX primary scanner.
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // OpenCV 4.14 trimmed to core, imgproc and photo: a fifth of the Maven artifact's native code.
    // Built from unmodified sources by scripts/opencv/build-opencv-android.sh (see scripts/opencv/README.md).
    implementation(project(":opencv"))

    // Google scanner remains as a compatibility fallback.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    // Text recognition and barcode scanning run in Google Play services, so their engines and models (about
    // 16 MB per CPU type for OCR alone) are not shipped inside the app. Latin OCR and barcodes are fetched at
    // install time (see AndroidManifest); the other scripts download the first time they are used.
    // -Pscanpro.bundledMl=true ships the models inside the APK instead (about 16 MB larger per CPU type), for
    // phones and test devices without a usable Google Play services. The code is the same either way.
    if (providers.gradleProperty("scanpro.bundledMl").orNull == "true") {
        implementation("com.google.mlkit:text-recognition:16.0.1")
        implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
        implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
        implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
        implementation("com.google.mlkit:text-recognition-korean:16.0.1")
        implementation("com.google.mlkit:barcode-scanning:17.3.0")
    } else {
        implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
        implementation("com.google.android.gms:play-services-mlkit-text-recognition-devanagari:16.0.1")
        implementation("com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1")
        implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
        implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")
        implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    }
    // ML Kit Translate and language identification live in the on-demand :translate module, or in the app
    // itself for sideload builds.
    implementation("com.google.android.play:feature-delivery:2.1.0")
    if (sideload) {
        implementation("com.google.mlkit:translate:17.0.3")
        implementation("com.google.mlkit:language-id:17.0.6")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Room + Hilt + WorkManager.
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    // The Files list is paged from the database instead of held in memory.
    implementation("androidx.room:room-paging:2.8.5")
    implementation("androidx.paging:paging-runtime:3.5.3")
    implementation("androidx.paging:paging-compose:3.5.3")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("androidx.hilt:hilt-compiler:1.4.0")

    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Apache-2.0 PDF engine for merge/split/import and password protection.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    // The real org.json: the Android jar's version only has stubs, and the backup format is JSON.
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
