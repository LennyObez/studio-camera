plugins {
    id("studiocamera.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:designsystem"))
        }
    }
}
