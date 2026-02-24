package com.studiocamera.feature.pair.di

import com.studiocamera.core.domain.usecase.ParseQrPayloadUseCase
import com.studiocamera.feature.pair.domain.PairStateMachine
import com.studiocamera.feature.pair.presentation.DeviceListManager
import com.studiocamera.feature.pair.presentation.PairViewModel
import com.studiocamera.feature.pair.presentation.WifiDirectPairingManager
import org.koin.dsl.module

val pairModule = module {
    factory { ParseQrPayloadUseCase() }
    factory { PairStateMachine(pairRepository = get()) }
    factory { WifiDirectPairingManager(
        wifiDirectConnector = get(),
        deviceStorage = get(),
        sessionManager = get()
    ) }
    factory { DeviceListManager(
        deviceStorage = get(),
        connectionStateManager = get(),
        wifiDirectConnector = get(),
        sessionManager = get()
    ) }
    factory { PairViewModel(
        parseQrPayload = get(),
        pairStateMachine = get(),
        connectionStateManager = get(),
        wifiDirectConnector = get(),
        sessionManager = get(),
        wifiDirectPairingManager = get(),
        deviceListManager = get()
    ) }
}
