package com.studiocamera.core.storage.di

import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.storage.DeviceStorageImpl
import com.studiocamera.core.storage.SettingsRepositoryImpl
import org.koin.dsl.module

val storageModule = module {
    // SecureStorage is provided by platform-specific modules (Android: AppModule, iOS: KoinInit)
    single<DeviceStorageRepository> { DeviceStorageImpl(secureStorage = get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(secureStorage = get()) }
}
