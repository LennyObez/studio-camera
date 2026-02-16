plugins {
    id("studiocamera.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":core:network"))
            implementation(project(":core:data"))
            implementation(libs.coil.compose)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.rtsp)
            implementation(libs.media3.ui)
        }
    }
}
