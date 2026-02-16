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
}
