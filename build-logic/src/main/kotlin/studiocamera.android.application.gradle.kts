plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.studiocamera.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.studiocamera.android"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("STUDIO_CAMERA_VERSION_CODE")
            .getOrElse("1").toInt()
        versionName = providers.gradleProperty("STUDIO_CAMERA_VERSION_NAME")
            .getOrElse("1.0.0")
    }

    signingConfigs {
        // Release signing — expects environment variables or gradle.properties:
        //   STUDIO_CAMERA_KEYSTORE_PATH, STUDIO_CAMERA_KEYSTORE_PASSWORD,
        //   STUDIO_CAMERA_KEY_ALIAS, STUDIO_CAMERA_KEY_PASSWORD
        val keystorePath = providers.gradleProperty("STUDIO_CAMERA_KEYSTORE_PATH")
            .orElse(providers.environmentVariable("STUDIO_CAMERA_KEYSTORE_PATH"))
        if (keystorePath.isPresent) {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = providers.gradleProperty("STUDIO_CAMERA_KEYSTORE_PASSWORD")
                    .orElse(providers.environmentVariable("STUDIO_CAMERA_KEYSTORE_PASSWORD"))
                    .get()
                keyAlias = providers.gradleProperty("STUDIO_CAMERA_KEY_ALIAS")
                    .orElse(providers.environmentVariable("STUDIO_CAMERA_KEY_ALIAS"))
                    .get()
                keyPassword = providers.gradleProperty("STUDIO_CAMERA_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("STUDIO_CAMERA_KEY_PASSWORD"))
                    .get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing config if configured, otherwise debug
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}
