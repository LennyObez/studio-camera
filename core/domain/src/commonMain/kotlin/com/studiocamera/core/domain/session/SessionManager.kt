package com.studiocamera.core.domain.session

import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SessionManager {
    val state: StateFlow<ConnectionState>
    val events: SharedFlow<SessionEvent>

    suspend fun connect(device: PairedDevice)
    suspend fun disconnect()
    suspend fun reconnect()
    fun isConnected(): Boolean
    fun currentCapabilities(): DeviceCapabilities?
}
