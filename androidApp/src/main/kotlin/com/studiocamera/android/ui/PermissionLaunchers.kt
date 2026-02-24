package com.studiocamera.android.ui

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Holds the permission launcher state needed by [ContentRouter] to request
 * camera and nearby Wi-Fi permissions on behalf of the pairing flow.
 */
class PermissionLaunchers(
    val cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    val nearbyWifiPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val setCameraCallback: ((() -> Unit)?) -> Unit,
    private val setNearbyWifiCallback: (((Boolean) -> Unit)?) -> Unit
) {
    /**
     * Request the camera permission. When granted, [onGranted] is invoked.
     */
    fun requestCameraPermission(onGranted: () -> Unit) {
        setCameraCallback(onGranted)
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    /**
     * Request the NEARBY_WIFI_DEVICES permission (Android 13+).
     * [onResult] receives whether the permission was granted.
     */
    fun requestNearbyWifiPermission(onResult: (Boolean) -> Unit) {
        setNearbyWifiCallback(onResult)
        nearbyWifiPermissionLauncher.launch("android.permission.NEARBY_WIFI_DEVICES")
    }
}

/**
 * Creates and remembers a [PermissionLaunchers] instance wired to activity-result
 * launchers for camera and nearby Wi-Fi permissions.
 */
@Composable
fun rememberPermissionLaunchers(
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
): PermissionLaunchers {
    var pendingCameraCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingNearbyWifiCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingCameraCallback?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Camera permission is required to scan QR codes",
                    duration = SnackbarDuration.Short
                )
            }
        }
        pendingCameraCallback = null
    }

    val nearbyWifiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingNearbyWifiCallback?.invoke(isGranted)
        pendingNearbyWifiCallback = null
    }

    return remember(cameraLauncher, nearbyWifiLauncher) {
        PermissionLaunchers(
            cameraPermissionLauncher = cameraLauncher,
            nearbyWifiPermissionLauncher = nearbyWifiLauncher,
            setCameraCallback = { pendingCameraCallback = it },
            setNearbyWifiCallback = { pendingNearbyWifiCallback = it }
        )
    }
}
