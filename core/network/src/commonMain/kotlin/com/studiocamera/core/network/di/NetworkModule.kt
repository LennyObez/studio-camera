package com.studiocamera.core.network.di

import com.studiocamera.core.network.TlsConfig
import com.studiocamera.core.network.createHttpClient
import org.koin.dsl.module

val networkModule = module {
    single { TlsConfig() }
    single { createHttpClient(get()) }
}
