package com.studiocamera.android.di

import com.studiocamera.android.billing.GoogleBillingRepository
import com.studiocamera.android.BuildConfig
import com.studiocamera.core.common.MockModeManager
import com.studiocamera.core.common.platform.ShareHandler
import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.data.discovery.MdnsDiscoveryEngine
import com.studiocamera.core.data.platform.PlatformDownloader
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.data.di.dataModule
import com.studiocamera.core.network.TokenRefreshConfig
import com.studiocamera.core.domain.di.domainModule
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.network.di.networkModule
import com.studiocamera.core.storage.SecureStorage
import com.studiocamera.core.storage.di.storageModule
import com.studiocamera.feature.camera.di.cameraModule
import com.studiocamera.feature.discovery.di.discoveryModule
import com.studiocamera.feature.media.di.mediaModule
import com.studiocamera.feature.mock.di.mockModule
import com.studiocamera.feature.pair.di.pairModule
import com.studiocamera.feature.settings.di.settingsModule
import org.koin.core.qualifier.named
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
    mockModule,
    settingsModule,
    bridgeModule
)

val coreModule = module {
    single { ConnectionStateManager() }
    single { SecureStorage(get()) }
    single { MockModeManager() }
    single<String>(named("appVersion")) { BuildConfig.VERSION_NAME }
    single { MdnsDiscoveryEngine(get()) }
    single { PlatformDownloader(get()) }
    single { ShareHandler(get()) }
    single { WifiDirectConnector(get()) }
    single { GoogleBillingRepository(get(), get()) }
    single<BillingRepository> { get<GoogleBillingRepository>() }

    single {
        val connectionStateManager: ConnectionStateManager = get()
        val deviceStorage: DeviceStorageRepository = get()
        TokenRefreshConfig().apply {
            getEndpoint = { connectionStateManager.connectedDevice.value?.endpoint }
            getRefreshToken = {
                val deviceId = connectionStateManager.connectedDevice.value?.deviceId
                if (deviceId != null) deviceStorage.getSessionInfo(deviceId)?.refreshToken else null
            }
            onTokenRefreshed = { accessToken, refreshToken ->
                val deviceId = connectionStateManager.connectedDevice.value?.deviceId
                if (deviceId != null) {
                    val existing = deviceStorage.getSessionInfo(deviceId)
                    if (existing != null) {
                        deviceStorage.saveSessionInfo(existing.copy(accessToken = accessToken, refreshToken = refreshToken))
                    }
                }
            }
            onRefreshFailed = {
                connectionStateManager.updateState(com.studiocamera.core.domain.model.ConnectionState.Failed)
            }
        }
    }
}

/**
 * Bridge module: provides unqualified bindings that delegate to "real" or "mock"
 * based on MockModeManager state. The underlying instances are singletons;
 * the factory just routes to the correct one on each injection.
 */
val bridgeModule = module {
    factory<SessionManager> {
        val mockMode: MockModeManager = get()
        if (mockMode.isMockActive.value) get(named("mock")) else get(named("real"))
    }
    factory<CameraRepository> {
        val mockMode: MockModeManager = get()
        if (mockMode.isMockActive.value) get(named("mock")) else get(named("real"))
    }
    factory<MediaRepository> {
        val mockMode: MockModeManager = get()
        if (mockMode.isMockActive.value) get(named("mock")) else get(named("real"))
    }
}
