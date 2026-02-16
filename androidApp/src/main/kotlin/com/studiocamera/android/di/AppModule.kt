package com.studiocamera.android.di

import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.data.di.dataModule
import com.studiocamera.core.domain.di.domainModule
import com.studiocamera.core.network.di.networkModule
import com.studiocamera.core.storage.SecureStorage
import com.studiocamera.core.storage.di.storageModule
import com.studiocamera.feature.camera.di.cameraModule
import com.studiocamera.feature.discovery.di.discoveryModule
import com.studiocamera.feature.media.di.mediaModule
import com.studiocamera.feature.mock.di.mockModule
import com.studiocamera.feature.pair.di.pairModule
import org.koin.dsl.module

fun appModule() = listOf(
    coreModule,
    networkModule,
    storageModule,
    dataModule,
    domainModule,
    pairModule,
    discoveryModule,
    cameraModule,
    mediaModule,
    mockModule
)

val coreModule = module {
    single { ConnectionStateManager() }
    single { SecureStorage(get()) }
}
