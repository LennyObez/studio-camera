package com.studiocamera.core.ui

import co.touchlab.kermit.Logger
import com.studiocamera.core.common.SanitizingLogWriter
import com.studiocamera.core.data.billing.StoreKitBillingRepository
import com.studiocamera.core.data.di.dataModule
import com.studiocamera.core.domain.di.domainModule
import com.studiocamera.core.network.di.networkModule
import com.studiocamera.core.storage.di.storageModule
import com.studiocamera.core.ui.di.iosBridgeModule
import com.studiocamera.core.ui.di.iosCoreModule
import com.studiocamera.feature.camera.di.cameraModule
import com.studiocamera.feature.discovery.di.discoveryModule
import com.studiocamera.feature.media.di.mediaModule
import com.studiocamera.feature.mock.di.mockModule
import com.studiocamera.feature.pair.di.pairModule
import com.studiocamera.feature.settings.di.settingsModule
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * iOS Koin initialization entry point.
 *
 * Called from Swift: `KoinInit.shared.start()`
 * The `object` keyword exposes as a singleton with `.shared` in Swift via Kotlin/Native interop.
 */
object KoinInit : KoinComponent {

    fun start() {
        initLogging()

        startKoin {
            modules(
                iosCoreModule,
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
                iosBridgeModule
            )
        }

        // Initialize StoreKit billing after Koin is ready
        get<StoreKitBillingRepository>().initialize()

        Logger.i("KoinInit") { "iOS Koin initialized" }
    }

    private fun initLogging() {
        Logger.setLogWriters(
            SanitizingLogWriter(
                delegate = co.touchlab.kermit.platformLogWriter(),
                isRelease = false
            )
        )
        Logger.setTag("StudioCamera")
    }
}
