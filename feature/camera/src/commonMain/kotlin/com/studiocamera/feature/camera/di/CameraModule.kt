package com.studiocamera.feature.camera.di

import org.koin.dsl.module

val cameraModule = module {
    // CameraRepository is provided by DataModule (named "real") and MockModule (named "mock")
    // Bridged in AppModule
}
