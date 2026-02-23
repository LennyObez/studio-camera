plugins {
    id("studiocamera.android.application")
}


dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:storage"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:pair"))
    implementation(project(":feature:discovery"))
    implementation(project(":feature:camera"))
    implementation(project(":feature:media"))
    implementation(project(":feature:mock"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.decompose)
    implementation(libs.decompose.compose)
    implementation(libs.kermit)
    implementation(libs.play.billing)

    @Suppress("DEPRECATION")
    implementation(compose.runtime)
    @Suppress("DEPRECATION")
    implementation(compose.foundation)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
    @Suppress("DEPRECATION")
    implementation(compose.materialIconsExtended)
}
