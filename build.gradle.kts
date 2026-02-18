plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// Add clean task for root and structural wrapper projects (like :core, :feature)
allprojects {
    val isStructuralProject = childProjects.isNotEmpty()
    if (isStructuralProject || this == rootProject) {
        tasks.register<Delete>("clean") {
            delete(layout.buildDirectory)
        }
    }
}
