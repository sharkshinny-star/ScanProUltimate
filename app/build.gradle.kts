plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.example.scanpro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scanpro.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "4.2.0"

        vectorDrawables { useSupportLibrary = true }
        // True when the ML Kit models ship inside the APK (-Pscanpro.bundledMl=true): no Play-services download checks.
        buildConfigField("boolean", "BUNDLED_ML", (providers.gradleProperty("scanpro.bundledMl").orNull == "true").toString())
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

    // Shared APKs: one small APK per CPU type plus a universal one. Play Store bundles split by CPU on their own.
    splits {
        abi {
            isEnable = true
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

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "**/sike*",
                "**/picnic*"
            )
        }
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

    // CameraX primary scanner.
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // OpenCV 4.x is deliberately used instead of the newer 5.x major line for
    // a more conservative production baseline.
    implementation("org.opencv:opencv:4.14.0")

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
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Room + Hilt + WorkManager.
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
