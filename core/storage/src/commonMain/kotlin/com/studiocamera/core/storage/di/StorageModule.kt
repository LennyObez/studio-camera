package com.studiocamera.core.storage.di

import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.storage.DeviceStorageImpl
import org.koin.dsl.module

val storageModule = module {
    // SecureStorage is provided by platform-specific modules (Android: AppModule, iOS: KoinInit)
    single<DeviceStorageRepository> { DeviceStorageImpl(secureStorage = get()) }
}
