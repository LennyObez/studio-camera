package com.studiocamera.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                setTimeoutInterval(30.0)
            }
            // TLS fingerprint pinning handled via URLSession delegate in production
            // For now, rely on system trust store
        }
    }
}
