package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.DiscoveredDevice
import kotlinx.coroutines.flow.Flow

interface DiscoveryRepository {
    fun discoverDevices(): Flow<List<DiscoveredDevice>>
    fun startScanning()
    fun stopScanning()
    val isScanning: Flow<Boolean>
}
