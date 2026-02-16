package com.studiocamera.core.data.discovery

import com.studiocamera.core.domain.model.DiscoveredDevice
import com.studiocamera.core.domain.model.DiscoveryTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Common interface for mDNS discovery. Platform-specific implementations use
 * NsdManager (Android) and NSNetServiceBrowser (iOS).
 */
expect class MdnsDiscoveryEngine {
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    val isScanning: StateFlow<Boolean>
    fun startScanning()
    fun stopScanning()
}
