package com.studiocamera.feature.pair.presentation

import com.studiocamera.core.common.currentEpochSeconds
import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.common.platform.WifiDirectResult
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.ConnectionType
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.detectCameraBrand
import com.studiocamera.core.domain.model.formatCameraDisplayName
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.SessionManager
import co.touchlab.kermit.Logger

/**
 * Result of a Wi-Fi Direct pairing attempt.
 */
sealed class WifiDirectPairingResult {
    /** Successfully connected and camera session initialized. */
    data object Connected : WifiDirectPairingResult()

    /** Connected to Wi-Fi but camera API was unreachable. */
    data class ApiUnreachable(val message: String) : WifiDirectPairingResult()

    /** User cancelled the connection. */
    data class Cancelled(val message: String) : WifiDirectPairingResult()

    /** Connection failed with an error message. */
    data class Failed(val message: String) : WifiDirectPairingResult()

    /** Device doesn't support automatic Wi-Fi — user must connect manually. */
    data class OpenSettings(val ssid: String) : WifiDirectPairingResult()

    /** Needs the Nearby Wi-Fi Devices runtime permission. */
    data class NeedsPermission(val ssid: String, val password: String, val modelName: String?) : WifiDirectPairingResult()
}

/**
 * Encapsulates Wi-Fi Direct connection, camera brand detection, and device persistence.
 *
 * This class performs operations and returns results — it does not own UI state.
 * The calling ViewModel is responsible for state updates based on results.
 */
class WifiDirectPairingManager(
    private val wifiDirectConnector: WifiDirectConnector,
    private val deviceStorage: DeviceStorageRepository,
    private val sessionManager: SessionManager
) {

    /**
     * Connect to a camera's Wi-Fi Direct network, detect the brand, save the device,
     * and initialize the camera session.
     *
     * @param ssid The Wi-Fi SSID to connect to.
     * @param password The Wi-Fi password (may be blank for open networks).
     * @param modelName Optional model name hint (e.g. from a Sony QR code).
     * @return A [WifiDirectPairingResult] describing the outcome.
     */
    suspend fun connect(
        ssid: String,
        password: String,
        modelName: String?
    ): WifiDirectPairingResult {
        val result = wifiDirectConnector.connect(ssid, password)

        return when (result) {
            is WifiDirectResult.Connected -> {
                val brand = detectCameraBrand(ssid)
                val deviceName = if (modelName != null) {
                    formatCameraDisplayName(brand, modelName)
                } else {
                    brandDisplayName(brand, ssid)
                }

                val deviceId = "wd-${ssid.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
                val device = PairedDevice(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    endpoint = "http://${result.gatewayIp}",
                    fingerprint = "",
                    connectionType = ConnectionType.WifiDirect,
                    cameraBrand = brand,
                    wifiSsid = ssid,
                    lastConnectedAt = currentEpochSeconds()
                )

                // Initialize camera session before persisting — only save if API is reachable
                sessionManager.connect(device)

                if (sessionManager.isConnected()) {
                    deviceStorage.savePairedDevice(device)
                    if (password.isNotBlank()) {
                        deviceStorage.saveWifiPassword(deviceId, password)
                    }
                    WifiDirectPairingResult.Connected
                } else {
                    WifiDirectPairingResult.ApiUnreachable(
                        "Connected to camera Wi-Fi but could not reach camera API. " +
                            "Make sure your camera is in remote control mode."
                    )
                }
            }

            is WifiDirectResult.UserCancelled -> {
                WifiDirectPairingResult.Cancelled(
                    "Connection cancelled. You can try again or connect manually via Wi-Fi settings."
                )
            }

            is WifiDirectResult.Failed -> {
                WifiDirectPairingResult.Failed("Wi-Fi connection failed: ${result.reason}")
            }

            is WifiDirectResult.OpenWifiSettings -> {
                WifiDirectPairingResult.OpenSettings(ssid)
            }

            is WifiDirectResult.NeedsNearbyWifiPermission -> {
                WifiDirectPairingResult.NeedsPermission(ssid, password, modelName)
            }
        }
    }

    /**
     * Reconnect to a previously paired device and navigate to a destination.
     *
     * @param device The stored [PairedDevice] to reconnect to.
     * @param password The stored Wi-Fi password.
     * @return A [WifiDirectPairingResult] describing the outcome.
     */
    suspend fun reconnect(
        device: PairedDevice,
        password: String
    ): WifiDirectPairingResult {
        val ssid = device.wifiSsid
            ?: return WifiDirectPairingResult.Failed("Cannot reconnect -- no saved Wi-Fi network")

        val result = wifiDirectConnector.connect(ssid, password)

        return when (result) {
            is WifiDirectResult.Connected -> {
                val updatedDevice = device.copy(
                    endpoint = "http://${result.gatewayIp}",
                    lastConnectedAt = currentEpochSeconds()
                )
                deviceStorage.savePairedDevice(updatedDevice)
                sessionManager.connect(updatedDevice)

                if (sessionManager.isConnected()) {
                    WifiDirectPairingResult.Connected
                } else {
                    WifiDirectPairingResult.ApiUnreachable(
                        "Connected to Wi-Fi but camera API unreachable"
                    )
                }
            }

            is WifiDirectResult.NeedsNearbyWifiPermission -> {
                WifiDirectPairingResult.NeedsPermission(ssid, password, device.deviceName)
            }

            is WifiDirectResult.UserCancelled -> {
                WifiDirectPairingResult.Cancelled("")
            }

            is WifiDirectResult.Failed -> {
                WifiDirectPairingResult.Failed("Could not reconnect: ${result.reason}")
            }

            is WifiDirectResult.OpenWifiSettings -> {
                WifiDirectPairingResult.OpenSettings(ssid)
            }
        }
    }

    /**
     * Disconnect the current Wi-Fi Direct connection.
     */
    suspend fun disconnect() {
        wifiDirectConnector.disconnect()
    }

    private fun brandDisplayName(brand: CameraBrand, ssid: String): String = when (brand) {
        CameraBrand.Sony -> "Sony Camera"
        CameraBrand.Canon -> "Canon Camera"
        CameraBrand.Nikon -> "Nikon Camera"
        CameraBrand.Fujifilm -> "Fujifilm Camera"
        CameraBrand.Panasonic -> "Panasonic/Lumix Camera"
        CameraBrand.OmSystem -> "OM System Camera"
        CameraBrand.Unknown -> "Camera ($ssid)"
    }
}
