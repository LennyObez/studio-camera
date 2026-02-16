package com.studiocamera.core.ui.permission

/**
 * Platform-agnostic permission handling.
 * Requests permissions just-in-time with rationale dialogs.
 */
enum class AppPermission {
    Camera,
    Location,
    Bluetooth,
    Nfc
}

enum class PermissionStatus {
    Granted,
    Denied,
    PermanentlyDenied,
    NotDetermined
}

expect class PermissionHandler {
    fun checkPermission(permission: AppPermission): PermissionStatus
    suspend fun requestPermission(permission: AppPermission): PermissionStatus
}

fun PermissionStatus.isGranted(): Boolean = this == PermissionStatus.Granted
