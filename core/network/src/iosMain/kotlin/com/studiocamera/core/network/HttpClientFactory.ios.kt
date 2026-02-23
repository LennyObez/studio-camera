package com.studiocamera.core.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient {
    if (tlsConfig.trustedFingerprint != null) {
        Logger.w("HttpClientFactory") {
            "iOS TLS pinning not yet implemented — trustedFingerprint will be ignored. " +
                "Using system trust store."
        }
    }
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                setTimeoutInterval(30.0)
            }
        }
    }
}

actual fun createStreamingPlatformHttpClient(): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                // No timeout for streaming connections
                setTimeoutInterval(0.0)
            }
        }
    }
}
