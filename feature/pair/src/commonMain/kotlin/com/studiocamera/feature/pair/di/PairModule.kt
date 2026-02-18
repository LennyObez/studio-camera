package com.studiocamera.feature.pair.di

import com.studiocamera.core.domain.usecase.ParseQrPayloadUseCase
import com.studiocamera.feature.pair.domain.PairStateMachine
import com.studiocamera.feature.pair.presentation.PairViewModel
import org.koin.dsl.module

val pairModule = module {
    factory { ParseQrPayloadUseCase() }
    factory { PairStateMachine(pairRepository = get()) }
    single { PairViewModel(
        parseQrPayload = get(),
        pairStateMachine = get(),
        connectionStateManager = get(),
        deviceStorage = get(),
        wifiDirectConnector = get(),
        sessionManager = get()
    ) }
}
