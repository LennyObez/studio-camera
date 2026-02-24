package com.studiocamera.feature.settings.di

import com.studiocamera.feature.settings.presentation.SettingsViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val settingsModule = module {
    factory {
        SettingsViewModel(
            settingsRepository = get(),
            deviceStorage = get(),
            appVersion = getOrNull<String>(named("appVersion")) ?: "1.0.0"
        )
    }
}
