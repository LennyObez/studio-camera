package com.studiocamera.core.domain.session

import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.PairedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionStateManager {
    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _connectedDevice = MutableStateFlow<PairedDevice?>(null)
    val connectedDevice: StateFlow<PairedDevice?> = _connectedDevice.asStateFlow()

    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttempt: StateFlow<Int> = _reconnectAttempt.asStateFlow()

    val isConnected: Boolean
        get() = _state.value == ConnectionState.Connected

    fun updateState(newState: ConnectionState) {
        _state.value = newState
        if (newState == ConnectionState.Connected || newState == ConnectionState.Disconnected) {
            _reconnectAttempt.value = 0
        }
    }

    fun updateReconnectAttempt(attempt: Int, maxRetries: Int) {
        _reconnectAttempt.value = attempt
    }

    fun setConnectedDevice(device: PairedDevice?) {
        _connectedDevice.value = device
        if (device == null) {
            _state.value = ConnectionState.Disconnected
        }
    }

    fun disconnect() {
        _connectedDevice.value = null
        _state.value = ConnectionState.Disconnected
        _reconnectAttempt.value = 0
    }
}
