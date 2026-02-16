package com.studiocamera.core.domain.model

data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val transport: DiscoveryTransport,
    val signalStrength: Int? = null,
    val lastSeenAt: Long = 0L,
    val isPaired: Boolean = false
)

enum class DiscoveryTransport {
    MDNS,
    UDP,
    BLE
}
