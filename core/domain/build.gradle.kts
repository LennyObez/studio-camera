plugins {
    id("studiocamera.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
        }
    }
}
