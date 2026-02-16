package com.studiocamera.core.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

actual class PermissionHandler(
    private val context: Context
) {
    actual fun checkPermission(permission: AppPermission): PermissionStatus {
        val androidPermission = permission.toAndroidPermission()
        return when {
            ContextCompat.checkSelfPermission(context, androidPermission) ==
                    PackageManager.PERMISSION_GRANTED -> PermissionStatus.Granted
            else -> PermissionStatus.NotDetermined
        }
    }

    actual suspend fun requestPermission(permission: AppPermission): PermissionStatus {
        // Actual permission request is handled by the Activity/Compose permission API
        // This is a check-only implementation; real requests go through
        // rememberLauncherForActivityResult in the Compose layer
        return checkPermission(permission)
    }

    companion object {
        fun AppPermission.toAndroidPermission(): String = when (this) {
            AppPermission.Camera -> Manifest.permission.CAMERA
            AppPermission.Location -> Manifest.permission.ACCESS_FINE_LOCATION
            AppPermission.Bluetooth -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Manifest.permission.BLUETOOTH_SCAN
                } else {
                    Manifest.permission.BLUETOOTH
                }
            }
            AppPermission.Nfc -> Manifest.permission.NFC
        }
    }
}
