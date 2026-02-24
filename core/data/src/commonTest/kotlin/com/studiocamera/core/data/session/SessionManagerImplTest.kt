package com.studiocamera.core.data.session

import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.model.SessionEvent
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerImplTest {

    // ── Fake DeviceStorageRepository ──

    private class FakeDeviceStorage : DeviceStorageRepository {
        val trustedFingerprints = mutableMapOf<String, String>()
        private val sessions = mutableMapOf<String, SessionInfo>()
        private val devices = mutableMapOf<String, PairedDevice>()
        private val wifiPasswords = mutableMapOf<String, String>()

        override suspend fun savePairedDevice(device: PairedDevice) {
            devices[device.deviceId] = device
        }
        override suspend fun getPairedDevices(): List<PairedDevice> = devices.values.toList()
        override suspend fun getPairedDevice(deviceId: String): PairedDevice? = devices[deviceId]
        override suspend fun removePairedDevice(deviceId: String) { devices.remove(deviceId) }
        override suspend fun saveWifiPassword(deviceId: String, password: String) {
            wifiPasswords[deviceId] = password
        }
        override suspend fun getWifiPassword(deviceId: String): String? = wifiPasswords[deviceId]
        override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {
            trustedFingerprints[deviceId] = fingerprint
        }
        override suspend fun getTrustedFingerprint(deviceId: String): String? =
            trustedFingerprints[deviceId]
        override suspend fun saveSessionInfo(info: SessionInfo) {
            sessions[info.deviceId] = info
        }
        override suspend fun getSessionInfo(deviceId: String): SessionInfo? = sessions[deviceId]
        override suspend fun clearSessionInfo(deviceId: String) { sessions.remove(deviceId) }
        override suspend fun clearAll() {
            devices.clear(); sessions.clear()
            trustedFingerprints.clear(); wifiPasswords.clear()
        }
    }

    // ── TestHarness ──

    private data class TestHarness(
        val sessionManager: SessionManagerImpl,
        val connectionStateManager: ConnectionStateManager,
        val deviceStorage: FakeDeviceStorage,
        val scope: TestScope
    )

    /**
     * Creates a harness with the given mock HTTP engine behaviour.
     * By default all HTTP requests succeed with a JSON payload.
     */
    private fun createHarness(
        engineFails: Boolean = false
    ): TestHarness {
        val testDispatcher = UnconfinedTestDispatcher()
        val scope = TestScope(testDispatcher)
        val connectionStateManager = ConnectionStateManager()
        val deviceStorage = FakeDeviceStorage()

        val engine = MockEngine { _ ->
            if (engineFails) {
                throw RuntimeException("Connection refused")
            }
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(engine)

        val sessionManager = SessionManagerImpl(
            httpClient = httpClient,
            connectionStateManager = connectionStateManager,
            deviceStorage = deviceStorage,
            brandApiDiscovery = null,
            scope = scope
        )

        return TestHarness(sessionManager, connectionStateManager, deviceStorage, scope)
    }

    private fun sonyDevice(fingerprint: String = "AA:BB:CC") = PairedDevice(
        deviceId = "sony-001",
        deviceName = "Test Sony A7",
        endpoint = "http://192.168.1.1",
        fingerprint = fingerprint,
        cameraBrand = CameraBrand.Sony
    )

    private fun unknownDevice(fingerprint: String = "AA:BB:CC") = PairedDevice(
        deviceId = "box-001",
        deviceName = "Test Box",
        endpoint = "http://192.168.1.1",
        fingerprint = fingerprint,
        cameraBrand = CameraBrand.Unknown
    )

    // ── Tests ──

    @Test
    fun connectWithStandardBrandSkipsHealthCheckAndConnects() = runTest {
        val h = createHarness()

        h.sessionManager.connect(sonyDevice())
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Connected, h.sessionManager.state.value)
        assertEquals("sony-001", h.connectionStateManager.connectedDevice.value?.deviceId)
        assertTrue(h.sessionManager.isConnected())
    }

    @Test
    fun connectVerifiesFingerprintMismatchFails() = runTest {
        val h = createHarness()
        // Store trusted fingerprint AA:BB:CC, but device presents DD:EE:FF
        h.deviceStorage.trustedFingerprints["sony-001"] = "AA:BB:CC"

        val events = mutableListOf<SessionEvent>()
        val job = h.scope.backgroundScope.launch {
            h.sessionManager.events.collect { events.add(it) }
        }

        h.sessionManager.connect(sonyDevice(fingerprint = "DD:EE:FF"))
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Failed, h.sessionManager.state.value)
        val errorEvent = events.filterIsInstance<SessionEvent.Error>().firstOrNull()
        assertIs<SessionError.FingerprintMismatch>(errorEvent?.error)

        job.cancel()
    }

    @Test
    fun connectWithEmptyFingerprintWhenTrustedExistsFails() = runTest {
        val h = createHarness()
        // Store trusted fingerprint, but device presents empty string
        h.deviceStorage.trustedFingerprints["sony-001"] = "AA:BB:CC"

        val events = mutableListOf<SessionEvent>()
        val job = h.scope.backgroundScope.launch {
            h.sessionManager.events.collect { events.add(it) }
        }

        h.sessionManager.connect(sonyDevice(fingerprint = ""))
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Failed, h.sessionManager.state.value)
        val errorEvent = events.filterIsInstance<SessionEvent.Error>().firstOrNull()
        assertIs<SessionError.FingerprintMismatch>(errorEvent?.error)

        job.cancel()
    }

    @Test
    fun connectWithMatchingFingerprintSucceeds() = runTest {
        val h = createHarness()
        h.deviceStorage.trustedFingerprints["sony-001"] = "AA:BB:CC"

        h.sessionManager.connect(sonyDevice(fingerprint = "AA:BB:CC"))
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Connected, h.sessionManager.state.value)
    }

    @Test
    fun connectWithNoTrustedFingerprintSucceeds() = runTest {
        val h = createHarness()
        // No trusted fingerprint stored at all — TOFU first connection

        h.sessionManager.connect(sonyDevice())
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Connected, h.sessionManager.state.value)
    }

    @Test
    fun disconnectResetsAllState() = runTest {
        val h = createHarness()
        h.sessionManager.connect(sonyDevice())
        h.scope.advanceUntilIdle()
        assertEquals(ConnectionState.Connected, h.sessionManager.state.value)

        h.sessionManager.disconnect()
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Disconnected, h.sessionManager.state.value)
        assertNull(h.connectionStateManager.connectedDevice.value)
        assertNull(h.sessionManager.currentCapabilities())
        assertNull(h.sessionManager.currentEndpoint())
        assertNull(h.sessionManager.currentAccessToken())
        assertFalse(h.sessionManager.isConnected())
    }

    @Test
    fun reconnectTransitionsToReconnectingState() = runTest {
        val h = createHarness()

        // Connect first so reconnect has a device
        h.sessionManager.connect(sonyDevice())
        h.scope.advanceUntilIdle()
        assertEquals(ConnectionState.Connected, h.sessionManager.state.value)

        // Collect events
        val events = mutableListOf<SessionEvent>()
        val job = h.scope.backgroundScope.launch {
            h.sessionManager.events.collect { events.add(it) }
        }

        // Reconnect should transition through Reconnecting -> connect -> Connected
        h.sessionManager.reconnect()
        h.scope.advanceUntilIdle()

        // Should have reconnected successfully (Sony connect always succeeds)
        assertEquals(ConnectionState.Connected, h.sessionManager.state.value)

        // Should have emitted a Reconnecting state change
        val reconnectingEvent = events.filterIsInstance<SessionEvent.StateChanged>()
            .any { it.state == ConnectionState.Reconnecting }
        assertTrue(reconnectingEvent, "Should emit Reconnecting state during reconnect")

        job.cancel()
    }

    @Test
    fun connectWithUnknownBrandAndFailingHttpEmitsFailed() = runTest {
        // Unknown brand takes the Studio Box path which requires HTTP health check.
        // With a failing engine, the health check throws, and connect catches it -> Failed.
        val h = createHarness(engineFails = true)

        val events = mutableListOf<SessionEvent>()
        val job = h.scope.backgroundScope.launch {
            h.sessionManager.events.collect { events.add(it) }
        }

        h.sessionManager.connect(unknownDevice())
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Failed, h.sessionManager.state.value)
        val errorEvent = events.filterIsInstance<SessionEvent.Error>()
            .any { it.error is SessionError.DeviceUnreachable }
        assertTrue(errorEvent, "Should emit DeviceUnreachable when HTTP health check fails")

        job.cancel()
    }

    @Test
    fun reconnectWithNoCurrentDeviceIsNoOp() = runTest {
        val h = createHarness()

        // No device connected, reconnect should return immediately
        h.sessionManager.reconnect()
        h.scope.advanceUntilIdle()

        assertEquals(ConnectionState.Disconnected, h.sessionManager.state.value)
    }
}
