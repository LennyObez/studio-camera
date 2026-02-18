plugins {
    id("studiocamera.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":core:storage"))
        }
    }
}
