package com.studiocamera.core.domain.session

import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.PairedDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionStateManagerTest {

    private val manager = ConnectionStateManager()

    @Test
    fun initialState_isDisconnected() {
        assertEquals(ConnectionState.Disconnected, manager.state.value)
        assertNull(manager.connectedDevice.value)
        assertFalse(manager.isConnected)
    }

    @Test
    fun updateState_changesStateFlow() {
        manager.updateState(ConnectionState.Connecting)
        assertEquals(ConnectionState.Connecting, manager.state.value)

        manager.updateState(ConnectionState.Connected)
        assertEquals(ConnectionState.Connected, manager.state.value)
        assertTrue(manager.isConnected)
    }

    @Test
    fun setConnectedDevice_updatesDeviceFlow() {
        val device = PairedDevice(
            deviceId = "test-id",
            deviceName = "Test Device",
            endpoint = "https://192.168.1.10:8443",
            fingerprint = "AA:BB:CC",
            lastConnectedAt = 1000L
        )

        manager.setConnectedDevice(device)
        assertEquals(device, manager.connectedDevice.value)
    }

    @Test
    fun setConnectedDevice_null_setsDisconnected() {
        manager.updateState(ConnectionState.Connected)
        manager.setConnectedDevice(null)

        assertNull(manager.connectedDevice.value)
        assertEquals(ConnectionState.Disconnected, manager.state.value)
    }

    @Test
    fun disconnect_clearsDeviceAndState() {
        val device = PairedDevice(
            deviceId = "test-id",
            deviceName = "Test Device",
            endpoint = "https://host",
            fingerprint = "AA",
            lastConnectedAt = 1000L
        )

        manager.setConnectedDevice(device)
        manager.updateState(ConnectionState.Connected)
        assertTrue(manager.isConnected)

        manager.disconnect()

        assertNull(manager.connectedDevice.value)
        assertEquals(ConnectionState.Disconnected, manager.state.value)
        assertFalse(manager.isConnected)
    }

    @Test
    fun stateTransitions_fullLifecycle() {
        manager.updateState(ConnectionState.Connecting)
        assertEquals(ConnectionState.Connecting, manager.state.value)

        manager.updateState(ConnectionState.Authenticating)
        assertEquals(ConnectionState.Authenticating, manager.state.value)

        manager.updateState(ConnectionState.Binding)
        assertEquals(ConnectionState.Binding, manager.state.value)

        manager.updateState(ConnectionState.Connected)
        assertEquals(ConnectionState.Connected, manager.state.value)
        assertTrue(manager.isConnected)

        manager.updateState(ConnectionState.Reconnecting)
        assertEquals(ConnectionState.Reconnecting, manager.state.value)
        assertFalse(manager.isConnected)

        manager.updateState(ConnectionState.Failed)
        assertEquals(ConnectionState.Failed, manager.state.value)
    }
}
