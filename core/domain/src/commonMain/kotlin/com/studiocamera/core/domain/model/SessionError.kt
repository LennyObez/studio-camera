package com.studiocamera.core.domain.model

sealed class SessionError(
    val message: String,
    val isRetryable: Boolean
) {
    class WrongCredentials : SessionError(
        message = "Authentication failed. Please re-pair.",
        isRetryable = false
    )
    class DeviceUnreachable : SessionError(
        message = "Cannot reach device. Check network.",
        isRetryable = true
    )
    class HandshakeFailed : SessionError(
        message = "Handshake failed. Try again.",
        isRetryable = true
    )
    class FingerprintMismatch : SessionError(
        message = "Device identity changed. Re-pair required.",
        isRetryable = false
    )
    class IncompatibleFirmware(
        val deviceVersion: String,
        val minRequired: String
    ) : SessionError(
        message = "Firmware $deviceVersion not compatible. Min: $minRequired.",
        isRetryable = false
    )
    class PermissionMissing(val permission: String) : SessionError(
        message = "Required permissions not granted.",
        isRetryable = false
    )
    class MaxRetriesExceeded : SessionError(
        message = "Unable to reconnect after multiple attempts.",
        isRetryable = true
    )
    class SessionExpired : SessionError(
        message = "Session expired. Reconnecting...",
        isRetryable = true
    )
    class BackgroundKilled : SessionError(
        message = "Connection lost (battery optimization). Reconnecting...",
        isRetryable = true
    )
    class Unknown(val cause: Throwable? = null) : SessionError(
        message = "An unexpected error occurred.",
        isRetryable = true
    )
}
