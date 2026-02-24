package com.studiocamera.core.data.repository

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.network.ApiEndpoints
import com.studiocamera.core.domain.repository.PairRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class PairRepositoryImpl(
    private val httpClient: HttpClient,
    private val deviceStorage: DeviceStorageRepository
) : PairRepository {

    override suspend fun resolveEndpoint(endpoint: String) {
        Logger.d("Pair") { "Resolving endpoint: $endpoint" }
        // Validate URL format
        require(endpoint.isNotBlank()) { "Endpoint is empty" }
        require(endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            "Endpoint must start with http:// or https://"
        }
        // Validate URL structure: must have a valid host (not just protocol prefix)
        val hostRegex = Regex("""^https?://([a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?\.)*[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(:\d{1,5})?(/.*)?$""")
        val ipRegex = Regex("""^https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d{1,5})?(/.*)?$""")
        require(hostRegex.matches(endpoint) || ipRegex.matches(endpoint)) {
            "Endpoint has an invalid URL format"
        }
        // Perform health check
        httpClient.get("$endpoint${ApiEndpoints.HEALTH}")
        Logger.d("Pair") { "Endpoint resolved successfully" }
    }

    override suspend fun performTlsHandshake(endpoint: String): String {
        Logger.d("Pair") { "Performing TLS handshake" }
        // The actual TLS handshake + fingerprint extraction happens at the OkHttp/Darwin level.
        // The server should include its certificate fingerprint in the response header.
        val response = httpClient.get("$endpoint${ApiEndpoints.HEALTH}")
        return response.headers["X-Certificate-Fingerprint"]
            ?: throw IllegalStateException(
                "Server did not provide certificate fingerprint. " +
                    "Cannot establish trust without identity verification."
            )
    }

    override suspend fun getTrustedFingerprint(deviceId: String): String? {
        return deviceStorage.getTrustedFingerprint(deviceId)
    }

    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {
        deviceStorage.saveTrustedFingerprint(deviceId, fingerprint)
    }

    override suspend fun authenticate(
        endpoint: String,
        bindToken: String
    ): Pair<String, String> {
        Logger.d("Pair") { "Authenticating with bind token" }
        // bindToken is intentionally NOT logged
        val response = httpClient.post("$endpoint${ApiEndpoints.BIND}") {
            contentType(ContentType.Application.Json)
            setBody(BindRequest(bindToken = bindToken))
        }
        val body = response.body<BindResponse>()
        return Pair(body.accessToken, body.refreshToken)
    }

    override suspend fun bind(
        deviceId: String,
        deviceName: String,
        endpoint: String,
        fingerprint: String,
        accessToken: String,
        refreshToken: String
    ): PairedDevice {
        Logger.d("Pair") { "Binding device: $deviceName" }

        val device = PairedDevice(
            deviceId = deviceId,
            deviceName = deviceName,
            endpoint = endpoint,
            fingerprint = fingerprint,
            lastConnectedAt = com.studiocamera.core.common.currentTimeMillis()
        )

        deviceStorage.savePairedDevice(device)
        deviceStorage.saveSessionInfo(
            com.studiocamera.core.domain.model.SessionInfo(
                deviceId = deviceId,
                deviceName = deviceName,
                endpoint = endpoint,
                accessToken = accessToken,
                refreshToken = refreshToken,
                connectedAt = com.studiocamera.core.common.currentTimeMillis()
            )
        )

        return device
    }

    override suspend fun negotiateCapabilities(
        endpoint: String,
        accessToken: String
    ): DeviceCapabilities {
        Logger.d("Pair") { "Negotiating capabilities" }
        val response = httpClient.get("$endpoint${ApiEndpoints.INFO}") {
            bearerAuth(accessToken)
        }
        return response.body<DeviceCapabilities>()
    }
}

@Serializable
private data class BindRequest(
    val bindToken: String
)

@Serializable
private data class BindResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 3600
)
