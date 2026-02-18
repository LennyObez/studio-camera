package com.studiocamera.core.domain.usecase

import com.studiocamera.core.common.currentEpochSeconds
import com.studiocamera.core.domain.model.ParseResult
import com.studiocamera.core.domain.model.QrPayload
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

class ParseQrPayloadUseCase(
    private val currentTimeSeconds: () -> Long = { currentEpochSeconds() }
) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val SUPPORTED_VERSION = 1
        private const val MAX_PAYLOAD_SIZE = 4096
    }

    operator fun invoke(rawPayload: String): ParseResult {
        if (rawPayload.length > MAX_PAYLOAD_SIZE) {
            return ParseResult.Invalid("Payload too large (${rawPayload.length} chars)")
        }

        val trimmed = rawPayload.trim()

        // Check for Sony Wi-Fi Direct format (e.g. "W01:S:1YE1;P:KN9bWfc9;C:ILCE-7M3;M:D8106828244D;")
        if (trimmed.startsWith("W01:")) {
            return parseSonyWifiDirect(trimmed)
        }

        // Check for standard Wi-Fi QR format (e.g. "WIFI:T:WPA;S:MyNetwork;P:password123;H:false;;")
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            return parseStandardWifiQr(trimmed)
        }

        return try {
            val payload = json.decodeFromString<QrPayload>(trimmed)
            validate(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-JSON content that isn't a recognized format
            ParseResult.UnrecognizedFormat(trimmed)
        }
    }

    /**
     * Parses standard WIFI: QR format per WPA spec.
     * Handles escaped characters: \; \\ \, \" within field values.
     */
    private fun parseStandardWifiQr(raw: String): ParseResult {
        val body = raw.removePrefix("WIFI:").removeSuffix(";;").removeSuffix(";")
        val fields = mutableMapOf<String, String>()

        // Parse fields handling escape sequences (\; \\ \, \")
        var i = 0
        while (i < body.length) {
            val keyEnd = body.indexOf(':', i)
            if (keyEnd < 0) break
            val key = body.substring(i, keyEnd)

            val valueStart = keyEnd + 1
            val value = StringBuilder()
            var j = valueStart
            while (j < body.length) {
                val ch = body[j]
                if (ch == '\\' && j + 1 < body.length) {
                    // Escape sequence: take the next character literally
                    value.append(body[j + 1])
                    j += 2
                } else if (ch == ';') {
                    break
                } else {
                    value.append(ch)
                    j++
                }
            }
            fields[key] = value.toString()
            i = j + 1 // skip the ';'
        }

        val ssid = fields["S"] ?: return ParseResult.UnrecognizedFormat(raw)
        val password = fields["P"] ?: ""
        val authType = fields["T"] ?: "WPA"

        return ParseResult.WifiCredentials(
            ssid = ssid,
            password = password,
            authType = authType
        )
    }

    private fun parseSonyWifiDirect(raw: String): ParseResult {
        val fields = mutableMapOf<String, String>()
        // Remove "W01:" prefix, then split on ";"
        val body = raw.removePrefix("W01:")
        body.split(";").forEach { segment ->
            val sep = segment.indexOf(':')
            if (sep > 0) {
                fields[segment.substring(0, sep)] = segment.substring(sep + 1)
            }
        }

        val ssidSuffix = fields["S"] ?: return ParseResult.UnrecognizedFormat(raw)
        val password = fields["P"] ?: return ParseResult.UnrecognizedFormat(raw)
        val model = fields["C"] ?: "Unknown Sony"
        val mac = fields["M"] ?: ""

        return ParseResult.SonyDevice(
            ssidSuffix = ssidSuffix,
            password = password,
            modelName = model,
            macAddress = mac
        )
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
