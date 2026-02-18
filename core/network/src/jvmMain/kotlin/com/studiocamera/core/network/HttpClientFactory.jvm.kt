package com.studiocamera.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient {
    return HttpClient(OkHttp)
}

actual fun createStreamingPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp)
}
