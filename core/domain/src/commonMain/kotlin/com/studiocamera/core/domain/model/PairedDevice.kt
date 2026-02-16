package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val fingerprint: String,
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    val lastConnectedAt: Long = 0L,
    val isPrimary: Boolean = false
)
