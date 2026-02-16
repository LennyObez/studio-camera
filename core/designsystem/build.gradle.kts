plugins {
    id("studiocamera.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:domain"))
        }
    }
}
