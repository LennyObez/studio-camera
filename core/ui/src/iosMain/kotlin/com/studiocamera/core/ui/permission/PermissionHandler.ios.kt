package com.studiocamera.core.ui.permission

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class PermissionHandler {

    actual fun checkPermission(permission: AppPermission): PermissionStatus {
        return when (permission) {
            AppPermission.Camera -> checkCameraPermission()
            AppPermission.Location -> checkLocationPermission()
            AppPermission.Bluetooth -> PermissionStatus.Granted // Bluetooth doesn't need runtime permission on iOS
            AppPermission.Nfc -> PermissionStatus.Granted // NFC doesn't need runtime permission on iOS
        }
    }

    actual suspend fun requestPermission(permission: AppPermission): PermissionStatus {
        return when (permission) {
            AppPermission.Camera -> requestCameraPermission()
            AppPermission.Location -> requestLocationPermission()
            AppPermission.Bluetooth -> PermissionStatus.Granted
            AppPermission.Nfc -> PermissionStatus.Granted
        }
    }

    private fun checkCameraPermission(): PermissionStatus {
        return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private suspend fun requestCameraPermission(): PermissionStatus =
        suspendCancellableCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                cont.resume(if (granted) PermissionStatus.Granted else PermissionStatus.Denied)
            }
        }

    private fun checkLocationPermission(): PermissionStatus {
        return when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.Granted
            kCLAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
            kCLAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private suspend fun requestLocationPermission(): PermissionStatus {
        // Location permission request requires CLLocationManager delegate pattern
        // For now, return current status - real implementation uses delegate
        return checkLocationPermission()
    }
}
