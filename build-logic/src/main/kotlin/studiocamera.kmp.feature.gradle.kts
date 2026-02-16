import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("studiocamera.kmp.library")
    id("studiocamera.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.findLibrary("decompose").get())
            implementation(libs.findLibrary("decompose-compose").get())
            implementation(libs.findLibrary("koin-compose").get())
            implementation(libs.findLibrary("koin-compose-viewmodel").get())
        }
    }
}
