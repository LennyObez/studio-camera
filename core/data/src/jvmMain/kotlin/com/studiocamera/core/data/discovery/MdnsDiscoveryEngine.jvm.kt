package com.studiocamera.core.data.discovery

import com.studiocamera.core.domain.model.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MdnsDiscoveryEngine {
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    actual val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    actual val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    actual fun startScanning() {
        _isScanning.value = true
        // No-op on JVM — mDNS discovery is platform-specific
    }

    actual fun stopScanning() {
        _isScanning.value = false
    }
}
