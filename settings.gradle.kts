pluginManagement {
    includeBuild("build-logic")
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

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
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

rootProject.name = "studio-camera"

// App modules
include(":androidApp")

// Core modules
include(":core:common")
include(":core:network")
include(":core:domain")
include(":core:data")
include(":core:storage")
include(":core:designsystem")
include(":core:ui")

// Feature modules
include(":feature:pair")
include(":feature:discovery")
include(":feature:camera")
include(":feature:media")
include(":feature:mock")
include(":feature:settings")
