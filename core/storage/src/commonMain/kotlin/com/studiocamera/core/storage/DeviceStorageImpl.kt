package com.studiocamera.core.storage

import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeviceStorageImpl(
    private val secureStorage: SecureStorage
) : DeviceStorageRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val KEY_FINGERPRINT_PREFIX = "fingerprint_"
        private const val KEY_SESSION_PREFIX = "session_"
    }

    override suspend fun savePairedDevice(device: PairedDevice) {
        val devices = getPairedDevices().toMutableList()
        val existingIndex = devices.indexOfFirst { it.deviceId == device.deviceId }
        if (existingIndex >= 0) {
            devices[existingIndex] = device
        } else {
            devices.add(device)
        }
        val encoded = json.encodeToString(devices)
        secureStorage.putString(KEY_PAIRED_DEVICES, encoded)
    }

    override suspend fun getPairedDevices(): List<PairedDevice> {
        val raw = secureStorage.getString(KEY_PAIRED_DEVICES) ?: return emptyList()
        return try {
            json.decodeFromString<List<PairedDevice>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPairedDevice(deviceId: String): PairedDevice? {
        return getPairedDevices().find { it.deviceId == deviceId }
    }

    override suspend fun removePairedDevice(deviceId: String) {
        val devices = getPairedDevices().filter { it.deviceId != deviceId }
        val encoded = json.encodeToString(devices)
        secureStorage.putString(KEY_PAIRED_DEVICES, encoded)
        secureStorage.remove("$KEY_FINGERPRINT_PREFIX$deviceId")
        secureStorage.remove("$KEY_SESSION_PREFIX$deviceId")
    }

    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {
        secureStorage.putString("$KEY_FINGERPRINT_PREFIX$deviceId", fingerprint)
    }

    override suspend fun getTrustedFingerprint(deviceId: String): String? {
        return secureStorage.getString("$KEY_FINGERPRINT_PREFIX$deviceId")
    }

    override suspend fun saveSessionInfo(info: SessionInfo) {
        val encoded = json.encodeToString(info)
        secureStorage.putString("$KEY_SESSION_PREFIX${info.deviceId}", encoded)
    }

    override suspend fun getSessionInfo(deviceId: String): SessionInfo? {
        val raw = secureStorage.getString("$KEY_SESSION_PREFIX$deviceId") ?: return null
        return try {
            json.decodeFromString<SessionInfo>(raw)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun clearSessionInfo(deviceId: String) {
        secureStorage.remove("$KEY_SESSION_PREFIX$deviceId")
    }

    override suspend fun clearAll() {
        secureStorage.clear()
    }
}
