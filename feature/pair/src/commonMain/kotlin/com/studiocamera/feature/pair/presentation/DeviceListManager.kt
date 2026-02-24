package com.studiocamera.feature.pair.presentation

import co.touchlab.kermit.Logger
import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager

/**
 * Manages the paired-device list: loading, renaming, and removing devices.
 *
 * This class performs operations and returns results — it does not own UI state.
 * The calling ViewModel is responsible for state updates based on results.
 */
class DeviceListManager(
    private val deviceStorage: DeviceStorageRepository,
    private val connectionStateManager: ConnectionStateManager,
    private val wifiDirectConnector: WifiDirectConnector,
    private val sessionManager: SessionManager
) {

    /**
     * Load all paired devices from storage.
     *
     * @return The current list of [PairedDevice]s.
     */
    suspend fun loadPairedDevices(): List<PairedDevice> {
        return deviceStorage.getPairedDevices()
    }

    /**
     * Rename a paired device. A blank [newName] clears the custom name.
     *
     * @param deviceId The device to rename.
     * @param newName The new custom display name (trimmed; blank means clear).
     * @return The updated list of paired devices, or null if the device was not found.
     */
    suspend fun renameDevice(deviceId: String, newName: String): List<PairedDevice>? {
        return try {
            val device = deviceStorage.getPairedDevice(deviceId) ?: return null
            val updated = device.copy(customName = newName.trim().ifBlank { null })
            deviceStorage.savePairedDevice(updated)
            deviceStorage.getPairedDevices()
        } catch (e: Exception) {
            Logger.e("DeviceListManager") { "Failed to rename device $deviceId: ${e.message}" }
            null
        }
    }

    /**
     * Remove a paired device from storage. If the device is currently connected,
     * it will be disconnected first.
     *
     * @param deviceId The device to remove.
     * @return The updated list of paired devices after removal, or the current list on error.
     */
    suspend fun removeDevice(deviceId: String): List<PairedDevice> {
        try {
            // Capture connected device ID atomically before any async work
            val isConnected = connectionStateManager.connectedDevice.value?.deviceId == deviceId
            if (isConnected) {
                wifiDirectConnector.disconnect()
                sessionManager.disconnect()
            }
            deviceStorage.removePairedDevice(deviceId)
        } catch (e: Exception) {
            Logger.e("DeviceListManager") { "Failed to remove device $deviceId: ${e.message}" }
        }
        return deviceStorage.getPairedDevices()
    }

    /**
     * Look up a paired device and its stored Wi-Fi password.
     *
     * @param deviceId The device to look up.
     * @return A pair of (device, password) or null if not found.
     */
    suspend fun getDeviceWithPassword(deviceId: String): Pair<PairedDevice, String>? {
        val device = deviceStorage.getPairedDevice(deviceId) ?: return null
        val password = deviceStorage.getWifiPassword(deviceId) ?: ""
        return device to password
    }
}
