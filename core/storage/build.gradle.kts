plugins {
    id("studiocamera.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.serialization)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
        }
    }
}
