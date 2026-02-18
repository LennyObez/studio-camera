package com.studiocamera.core.ui.permission

actual class PermissionHandler {
    actual fun checkPermission(permission: AppPermission): PermissionStatus {
        // JVM stub — permissions always granted for test/desktop
        return PermissionStatus.Granted
    }

    actual suspend fun requestPermission(permission: AppPermission): PermissionStatus {
        return PermissionStatus.Granted
    }
}
