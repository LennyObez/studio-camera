package com.studiocamera.feature.camera.di

import com.studiocamera.feature.camera.presentation.CameraViewModel
import org.koin.dsl.module

val cameraModule = module {
    // CameraRepository is provided by DataModule (named "real") and MockModule (named "mock")
    // Bridged in AppModule

    factory {
        CameraViewModel(
            cameraRepository = get(),
            sessionManager = get(),
            settingsRepository = get(),
            mockModeManager = get(),
            connectionStateManager = get()
        )
    }
}
