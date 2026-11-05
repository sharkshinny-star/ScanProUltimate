// Google's Maven repository is asked only for Google and AndroidX groups (the filter Android Studio's project template
// uses). Every other group goes straight to Maven Central: its lookups never reach Google's repository, cannot be
// answered from there, and no longer wait on a miss there first.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "ScanProUltimate"
include(":app")
include(":baselineprofile")
include(":translate")
include(":opencv")
