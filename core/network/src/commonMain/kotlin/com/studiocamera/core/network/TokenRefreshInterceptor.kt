package com.studiocamera.core.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
private data class RefreshRequest(val refreshToken: String)

@Serializable
private data class RefreshResponse(val accessToken: String, val refreshToken: String)

class TokenRefreshConfig {
    var getEndpoint: (() -> String?)? = null
    var getRefreshToken: (suspend () -> String?)? = null
    var onTokenRefreshed: (suspend (accessToken: String, refreshToken: String) -> Unit)? = null
    var onRefreshFailed: (() -> Unit)? = null
}

val TokenRefreshPlugin = createClientPlugin("TokenRefresh", ::TokenRefreshConfig) {
    val getEndpoint = pluginConfig.getEndpoint ?: return@createClientPlugin
    val getRefreshToken = pluginConfig.getRefreshToken ?: return@createClientPlugin
    val onTokenRefreshed = pluginConfig.onTokenRefreshed
    val onRefreshFailed = pluginConfig.onRefreshFailed

    val refreshMutex = Mutex()

    on(Send) { request ->
        val originalCall = proceed(request)

        if (originalCall.response.status != HttpStatusCode.Unauthorized) {
            return@on originalCall
        }

        // Skip refresh for the refresh endpoint itself
        val url = request.url.toString()
        if (url.contains(ApiEndpoints.AUTH_REFRESH)) {
            return@on originalCall
        }

        Logger.d("TokenRefresh") { "Got 401, attempting token refresh" }

        val refreshed = refreshMutex.withLock {
            val endpoint = getEndpoint() ?: return@withLock false
            val refreshToken = getRefreshToken() ?: return@withLock false

            try {
                val refreshResponse = client.post("$endpoint${ApiEndpoints.AUTH_REFRESH}") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }

                if (refreshResponse.status == HttpStatusCode.OK) {
                    val tokens = refreshResponse.body<RefreshResponse>()
                    onTokenRefreshed?.invoke(tokens.accessToken, tokens.refreshToken)
                    Logger.d("TokenRefresh") { "Token refreshed successfully" }
                    true
                } else {
                    Logger.w("TokenRefresh") { "Refresh failed with status: ${refreshResponse.status}" }
                    false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("TokenRefresh", e) { "Token refresh error" }
                false
            }
        }

        if (refreshed) {
            // Retry the original request — the caller's bearerAuth lambda
            // will pick up the new token from storage on the next call
            proceed(request)
        } else {
            onRefreshFailed?.invoke()
            originalCall
        }
    }
}
