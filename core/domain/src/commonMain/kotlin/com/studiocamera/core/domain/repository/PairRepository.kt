package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice

interface PairRepository {
    suspend fun resolveEndpoint(endpoint: String)
    suspend fun performTlsHandshake(endpoint: String): String
    suspend fun getTrustedFingerprint(deviceId: String): String?
    suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String)
    suspend fun authenticate(endpoint: String, bindToken: String): Pair<String, String>
    suspend fun bind(
        deviceId: String,
        deviceName: String,
        endpoint: String,
        fingerprint: String,
        accessToken: String,
        refreshToken: String
    ): PairedDevice
    suspend fun negotiateCapabilities(endpoint: String, accessToken: String): DeviceCapabilities
}
