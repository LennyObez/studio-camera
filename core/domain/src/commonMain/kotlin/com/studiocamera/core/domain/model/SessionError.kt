package com.studiocamera.core.domain.model

sealed class SessionError(
    val message: String,
    val isRetryable: Boolean
) {
    data object WrongCredentials : SessionError(
        message = "Authentication failed. Please re-pair.",
        isRetryable = false
    )
    data object DeviceUnreachable : SessionError(
        message = "Cannot reach device. Check network.",
        isRetryable = true
    )
    data object HandshakeFailed : SessionError(
        message = "Handshake failed. Try again.",
        isRetryable = true
    )
    data object FingerprintMismatch : SessionError(
        message = "Device identity changed. Re-pair required.",
        isRetryable = false
    )
    data class IncompatibleFirmware(
        val deviceVersion: String,
        val minRequired: String
    ) : SessionError(
        message = "Firmware $deviceVersion not compatible. Min: $minRequired.",
        isRetryable = false
    )
    data class PermissionMissing(val permission: String) : SessionError(
        message = "Required permissions not granted.",
        isRetryable = false
    )
    data object MaxRetriesExceeded : SessionError(
        message = "Unable to reconnect after multiple attempts.",
        isRetryable = true
    )
    data object SessionExpired : SessionError(
        message = "Session expired. Reconnecting...",
        isRetryable = true
    )
    data object BackgroundKilled : SessionError(
        message = "Connection lost (battery optimization). Reconnecting...",
        isRetryable = true
    )
    data class Unknown(val cause: Throwable? = null) : SessionError(
        message = "An unexpected error occurred.",
        isRetryable = true
    )
}
