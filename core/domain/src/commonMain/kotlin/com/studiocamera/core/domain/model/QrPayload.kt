package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QrPayload(
    val v: Int,
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val fingerprint: String,
    val bindToken: String,
    val expiresAt: Long
)

sealed class ParseResult {
    data class Success(val payload: QrPayload) : ParseResult()
    data class Expired(val expiresAt: Long) : ParseResult()
    data class UnsupportedVersion(val version: Int) : ParseResult()
    data class Invalid(val reason: String) : ParseResult()
    data class SonyDevice(
        val ssidSuffix: String,
        val password: String,
        val modelName: String,
        val macAddress: String
    ) : ParseResult()
    data class WifiCredentials(
        val ssid: String,
        val password: String,
        val authType: String = "WPA"
    ) : ParseResult()
    data class UnrecognizedFormat(val rawContent: String) : ParseResult()
}
