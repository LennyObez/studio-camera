package com.studiocamera.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient

fun createHttpClient(tlsConfig: TlsConfig = TlsConfig()): HttpClient {
    return createPlatformHttpClient(tlsConfig).config {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            })
        }

        install(WebSockets) {
            pingIntervalMillis = 15_000L
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d("Ktor") { message }
                }
            }
            level = LogLevel.HEADERS
            sanitizeHeader { header ->
                header == "Authorization" || header == "X-Bind-Token"
            }
        }
    }
}
