package com.studiocamera.core.domain.usecase

import com.studiocamera.core.common.currentEpochSeconds
import com.studiocamera.core.domain.model.ParseResult
import com.studiocamera.core.domain.model.QrPayload
import kotlinx.serialization.json.Json

class ParseQrPayloadUseCase(
    private val currentTimeSeconds: () -> Long = { currentEpochSeconds() }
) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val SUPPORTED_VERSION = 1
    }

    operator fun invoke(rawPayload: String): ParseResult {
        return try {
            val payload = json.decodeFromString<QrPayload>(rawPayload)
            validate(payload)
        } catch (e: Exception) {
            ParseResult.Invalid("Failed to parse QR code: ${e.message}")
        }
    }

    private fun validate(payload: QrPayload): ParseResult {
        if (payload.v != SUPPORTED_VERSION) {
            return ParseResult.UnsupportedVersion(payload.v)
        }

        if (payload.expiresAt < currentTimeSeconds()) {
            return ParseResult.Expired(payload.expiresAt)
        }

        if (payload.deviceId.isBlank()) {
            return ParseResult.Invalid("Device ID is empty")
        }
        if (payload.endpoint.isBlank()) {
            return ParseResult.Invalid("Endpoint is empty")
        }
        if (payload.fingerprint.isBlank()) {
            return ParseResult.Invalid("Fingerprint is empty")
        }
        if (payload.bindToken.isBlank()) {
            return ParseResult.Invalid("Bind token is empty")
        }

        return ParseResult.Success(payload)
    }
}
