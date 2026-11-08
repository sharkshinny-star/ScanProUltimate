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
    // The app switches language itself (first launch and Settings), so every language ships in every install:
    // with Play's per-language splits a newly chosen language could be missing until Play delivered it.
    @Suppress("UnstableApiUsage")
    bundle {
        language { enableSplit = false }
    }
    namespace = "com.example.scanpro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scanpro.app"
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 7
        versionName = "4.2.0"

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
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
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
        sourceSets.getByName("main").kotlin.directories.add("../translate/src/main/java")
    } else {
        // Delivered by Google Play when first used; see TranslationFeature and translate/build.gradle.kts.
        dynamicFeatures += setOf(":translate")
    }

    testOptions {
        // Robolectric needs the merged resources and manifest (LaunchSmokeTest).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Each Android version Robolectric loads is a few hundred MB of framework classes.
            // CI (7 GB runner) passes -Pscanpro.testHeap=2560m; locally the default stays 4g.
            it.maxHeapSize = providers.gradleProperty("scanpro.testHeap").getOrElse("4g")
            // Robolectric's Android 16 reads file-descriptor internals that Java 21 keeps closed by default.
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            // Robolectric fetches each Android version itself; Google's mirror of Maven Central avoids rate limits.
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
            // -Pscanpro.screenshots=true renders every screen to app/build/screenshots (see ScreenScreenshotTest).
            it.systemProperty("scanpro.screenshots", (project.findProperty("scanpro.screenshots") ?: "false").toString())
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
    val composeBom = platform(libs.androidx.compose.bom)
    @Suppress("AvoidDuplicateDependencies")
    //noinspection GradleDependency
    implementation(composeBom)
    @Suppress("AvoidDuplicateDependencies")
    //noinspection GradleDependency
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    // One launch-screen implementation for every Android version, with a dark variant and a custom exit animation.
    implementation(libs.androidx.core.splashscreen)
    // Applies the libraries' bundled Compose baseline profiles on installs that bypass Play, so first runs are smoother.
    implementation(libs.androidx.profileinstaller)
    // Measured startup profile, recorded on a device by the :baselineprofile module.
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX primary scanner.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // OpenCV 4.14 trimmed to core, imgproc and photo: a fifth of the Maven artifact's native code.
    // Built from unmodified sources by scripts/opencv/build-opencv-android.sh (see scripts/opencv/README.md).
    implementation(project(":opencv"))

    // Google scanner remains as a compatibility fallback.
    implementation(libs.play.services.mlkit.document.scanner)
    // Text recognition and barcode scanning run in Google Play services, so their engines and models (about
    // 16 MB per CPU type for OCR alone) are not shipped inside the app. Latin OCR and barcodes are fetched at
    // install time (see AndroidManifest); the other scripts download the first time they are used.
    // -Pscanpro.bundledMl=true ships the models inside the APK instead (about 16 MB larger per CPU type), for
    // phones and test devices without a usable Google Play services. The code is the same either way.
    if (providers.gradleProperty("scanpro.bundledMl").orNull == "true") {
        implementation(libs.mlkit.text.recognition)
        implementation(libs.mlkit.text.recognition.devanagari)
        implementation(libs.mlkit.text.recognition.chinese)
        implementation(libs.mlkit.text.recognition.japanese)
        implementation(libs.mlkit.text.recognition.korean)
        implementation(libs.mlkit.barcode.scanning)
    } else {
        implementation(libs.play.services.mlkit.text.recognition)
        implementation(libs.play.services.mlkit.text.recognition.devanagari)
        implementation(libs.play.services.mlkit.text.recognition.chinese)
        implementation(libs.play.services.mlkit.text.recognition.japanese)
        implementation(libs.play.services.mlkit.text.recognition.korean)
        implementation(libs.play.services.mlkit.barcode.scanning)
    }
    // ML Kit Translate and language identification live in the on-demand :translate module, or in the app
    // itself for sideload builds.
    implementation(libs.mlkit.common)
    implementation(libs.play.feature.delivery)
    if (sideload) {
        implementation(libs.translate)
        implementation(libs.language.id)
    }
    implementation(libs.kotlinx.coroutines.play.services)
    // Google Play In-App Review: asked for once, quietly, after a few successful scans.
    implementation(libs.play.review.ktx)

    // Room + Hilt + WorkManager.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // The Files list is paged from the database instead of held in memory.
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    // Apache-2.0 PDF engine for merge/split/import and password protection.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit4)
    // Runs the real Android framework of each version on the JVM: the launch smoke test starts the app on API 24 to 36.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    // Screenshot tests: the real Compose screens rendered on the JVM (ScreenScreenshotTest).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // The real org.json: the Android jar's version only has stubs, and the backup format is JSON.
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

androidComponents {
    beforeVariants { variantBuilder ->
        (variantBuilder as? com.android.build.api.variant.HasUnitTestBuilder)?.enableUnitTest = variantBuilder.name == "debug"
    }
}





