package com.studiocamera.core.ui.di

import com.studiocamera.core.common.MockModeManager
import com.studiocamera.core.common.platform.ShareHandler
import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.data.billing.StoreKitBillingRepository
import com.studiocamera.core.data.discovery.MdnsDiscoveryEngine
import com.studiocamera.core.data.platform.PlatformDownloader
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.network.TokenRefreshConfig
import com.studiocamera.core.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * iOS-specific Koin module — mirrors Android's `coreModule` from AppModule.kt.
 *
 * Key difference: iOS platform classes have no-arg constructors (no `Context` needed).
 * Bridge module is shared via `com.studiocamera.core.data.di.bridgeModule`.
 */
val iosCoreModule = module {
    single<CoroutineScope>(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ConnectionStateManager() }
    single { SecureStorage() }
    single { MockModeManager() }
    single<String>(named("appVersion")) { "1.0.0" }
    single { MdnsDiscoveryEngine() }
    single { PlatformDownloader() }
    single { ShareHandler() }
    single { WifiDirectConnector() }
    single { StoreKitBillingRepository(get()) }
    single<BillingRepository> { get<StoreKitBillingRepository>() }

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
                connectionStateManager.updateState(ConnectionState.Failed)
            }
        }
    }
}
