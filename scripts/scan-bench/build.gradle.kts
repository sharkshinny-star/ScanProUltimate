// Desktop benchmark for the page detector (see README.md). Not part of the app build.
plugins { kotlin("jvm") version "2.0.21"; application }
repositories { mavenCentral() }
// Desktop OpenCV with native libraries for Linux, macOS and Windows; same Java API as the app's trimmed 4.14 build.
dependencies { implementation("org.openpnp:opencv:4.9.0-0") }
val detector = rootDir.resolve("../../app/src/main/java/com/example/scanpro/scanner")
val imaging = rootDir.resolve("../../app/src/main/java/com/example/scanpro/imaging")
val synced = layout.buildDirectory.dir("synced")
// The app's own detector and filter sources, compiled unchanged against the stubs in stubs/.
val syncDetector = tasks.register<Copy>("syncDetector") {
    from(detector) { include("DocumentEdgeDetector.kt", "QuadGeometry.kt", "PageQuality.kt", "ScanAlignment.kt", "LineQuadFinder.kt") }
    from(imaging) { include("MatFilters.kt") }
    into(synced)
}
sourceSets.main { kotlin.srcDirs("stubs", "src", synced) }
tasks.compileKotlin { dependsOn(syncDetector) }
application { mainClass.set("BenchKt"); applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true") }
