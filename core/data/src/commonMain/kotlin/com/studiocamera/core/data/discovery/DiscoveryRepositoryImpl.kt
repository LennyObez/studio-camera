package com.studiocamera.core.data.discovery

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.DiscoveredDevice
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.repository.DiscoveryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DiscoveryRepositoryImpl(
    private val mdnsEngine: MdnsDiscoveryEngine,
    private val deviceStorage: DeviceStorageRepository
) : DiscoveryRepository {

    private val _isScanning = MutableStateFlow(false)

    override fun discoverDevices(): Flow<List<DiscoveredDevice>> {
        return mdnsEngine.discoveredDevices.map { devices ->
            val pairedDeviceIds = deviceStorage.getPairedDevices().map { it.deviceId }.toSet()

            devices
                .filter { isNotStale(it) }
                .distinctBy { it.deviceId }
                .map { device ->
                    device.copy(isPaired = device.deviceId in pairedDeviceIds)
                }
                .sortedWith(compareByDescending<DiscoveredDevice> { it.isPaired }
                    .thenByDescending { it.lastSeenAt })
        }
    }

    override fun startScanning() {
        Logger.d("Discovery") { "Starting scan" }
        _isScanning.value = true
        mdnsEngine.startScanning()
    }

    override fun stopScanning() {
        Logger.d("Discovery") { "Stopping scan" }
        _isScanning.value = false
        mdnsEngine.stopScanning()
    }

    override val isScanning: Flow<Boolean>
        get() = combine(_isScanning, mdnsEngine.isScanning) { a, b -> a || b }

    private fun isNotStale(device: DiscoveredDevice): Boolean {
        val staleThresholdMs = 60_000L
        val now = com.studiocamera.core.common.currentTimeMillis()
        return (now - device.lastSeenAt) < staleThresholdMs
    }
}
