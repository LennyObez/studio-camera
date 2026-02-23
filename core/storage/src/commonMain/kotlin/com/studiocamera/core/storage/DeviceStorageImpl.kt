package com.studiocamera.core.storage

import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeviceStorageImpl(
    private val secureStorage: SecureStorage
) : DeviceStorageRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val storageMutex = Mutex()

    companion object {
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val KEY_WIFI_PREFIX = "wifi_"
        private const val KEY_FINGERPRINT_PREFIX = "fingerprint_"
        private const val KEY_SESSION_PREFIX = "session_"
    }

    override suspend fun savePairedDevice(device: PairedDevice) {
        storageMutex.withLock {
            val devices = readDevicesUnlocked().toMutableList()
            val existingIndex = devices.indexOfFirst { it.deviceId == device.deviceId }
            if (existingIndex >= 0) {
                devices[existingIndex] = device
            } else {
                devices.add(device)
            }
            val encoded = json.encodeToString(devices)
            secureStorage.putString(KEY_PAIRED_DEVICES, encoded)
        }
    }

    override suspend fun getPairedDevices(): List<PairedDevice> {
        storageMutex.withLock {
            return readDevicesUnlocked()
        }
    }

    override suspend fun getPairedDevice(deviceId: String): PairedDevice? {
        storageMutex.withLock {
            return readDevicesUnlocked().find { it.deviceId == deviceId }
        }
    }

    override suspend fun removePairedDevice(deviceId: String) {
        storageMutex.withLock {
            val devices = readDevicesUnlocked().filter { it.deviceId != deviceId }
            val encoded = json.encodeToString(devices)
            secureStorage.putString(KEY_PAIRED_DEVICES, encoded)
            secureStorage.remove("$KEY_WIFI_PREFIX$deviceId")
            secureStorage.remove("$KEY_FINGERPRINT_PREFIX$deviceId")
            secureStorage.remove("$KEY_SESSION_PREFIX$deviceId")
        }
    }

    override suspend fun saveWifiPassword(deviceId: String, password: String) {
        storageMutex.withLock {
            secureStorage.putString("$KEY_WIFI_PREFIX$deviceId", password)
        }
    }

    override suspend fun getWifiPassword(deviceId: String): String? {
        storageMutex.withLock {
            return secureStorage.getString("$KEY_WIFI_PREFIX$deviceId")
        }
    }

    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {
        storageMutex.withLock {
            secureStorage.putString("$KEY_FINGERPRINT_PREFIX$deviceId", fingerprint)
        }
    }

    override suspend fun getTrustedFingerprint(deviceId: String): String? {
        storageMutex.withLock {
            return secureStorage.getString("$KEY_FINGERPRINT_PREFIX$deviceId")
        }
    }

    override suspend fun saveSessionInfo(info: SessionInfo) {
        storageMutex.withLock {
            val encoded = json.encodeToString(info)
            secureStorage.putString("$KEY_SESSION_PREFIX${info.deviceId}", encoded)
        }
    }

    override suspend fun getSessionInfo(deviceId: String): SessionInfo? {
        storageMutex.withLock {
            val raw = secureStorage.getString("$KEY_SESSION_PREFIX$deviceId") ?: return null
            return try {
                json.decodeFromString<SessionInfo>(raw)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun clearSessionInfo(deviceId: String) {
        storageMutex.withLock {
            secureStorage.remove("$KEY_SESSION_PREFIX$deviceId")
        }
    }

    override suspend fun clearAll() {
        storageMutex.withLock {
            secureStorage.clear()
        }
    }

    /** Read devices without acquiring the lock — caller must hold storageMutex. */
    private fun readDevicesUnlocked(): List<PairedDevice> {
        val raw = secureStorage.getString(KEY_PAIRED_DEVICES) ?: return emptyList()
        return try {
            json.decodeFromString<List<PairedDevice>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
