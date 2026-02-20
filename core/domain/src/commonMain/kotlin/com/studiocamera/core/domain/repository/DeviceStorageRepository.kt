package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo

interface DeviceStorageRepository {
    suspend fun savePairedDevice(device: PairedDevice)
    suspend fun getPairedDevices(): List<PairedDevice>
    suspend fun getPairedDevice(deviceId: String): PairedDevice?
    suspend fun removePairedDevice(deviceId: String)
    suspend fun saveWifiPassword(deviceId: String, password: String)
    suspend fun getWifiPassword(deviceId: String): String?
    suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String)
    suspend fun getTrustedFingerprint(deviceId: String): String?
    suspend fun saveSessionInfo(info: SessionInfo)
    suspend fun getSessionInfo(deviceId: String): SessionInfo?
    suspend fun clearSessionInfo(deviceId: String)
    suspend fun clearAll()
}
