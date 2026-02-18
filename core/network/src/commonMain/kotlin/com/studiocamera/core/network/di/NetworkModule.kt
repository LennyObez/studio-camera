package com.studiocamera.core.network.di

import com.studiocamera.core.network.TlsConfig
import com.studiocamera.core.network.TokenRefreshConfig
import com.studiocamera.core.network.createHttpClient
import com.studiocamera.core.network.createStreamingHttpClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

val networkModule = module {
    single { TlsConfig() }
    single {
        val tokenRefreshConfig = getOrNull<TokenRefreshConfig>()
        createHttpClient(get(), tokenRefreshConfig)
    }
    // Streaming client — no read timeout, for live view and MJPEG streams
    single<HttpClient>(named("streaming")) { createStreamingHttpClient() }
}
