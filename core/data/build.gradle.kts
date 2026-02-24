plugins {
    id("studiocamera.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(project(":core:storage"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
