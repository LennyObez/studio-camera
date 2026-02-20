package com.studiocamera.feature.pair.presentation

import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.common.platform.WifiDirectResult
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionEvent
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.domain.usecase.ParseQrPayloadUseCase
import com.studiocamera.feature.pair.domain.PairStateMachine
import com.studiocamera.core.domain.repository.PairRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeDeviceStorage : DeviceStorageRepository {
    val devices = mutableListOf<PairedDevice>()
    private val wifiPasswords = mutableMapOf<String, String>()
    override suspend fun savePairedDevice(device: PairedDevice) {
        devices.removeAll { it.deviceId == device.deviceId }
        devices.add(device)
    }
    override suspend fun getPairedDevices(): List<PairedDevice> = devices.toList()
    override suspend fun getPairedDevice(deviceId: String) = devices.find { it.deviceId == deviceId }
    override suspend fun removePairedDevice(deviceId: String) {
        devices.removeAll { it.deviceId == deviceId }
        wifiPasswords.remove(deviceId)
    }
    override suspend fun saveWifiPassword(deviceId: String, password: String) { wifiPasswords[deviceId] = password }
    override suspend fun getWifiPassword(deviceId: String): String? = wifiPasswords[deviceId]
    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {}
    override suspend fun getTrustedFingerprint(deviceId: String): String? = null
    override suspend fun saveSessionInfo(info: SessionInfo) {}
    override suspend fun getSessionInfo(deviceId: String): SessionInfo? = null
    override suspend fun clearSessionInfo(deviceId: String) {}
    override suspend fun clearAll() { devices.clear(); wifiPasswords.clear() }
}

class FakeSessionManager : SessionManager {
    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state
    private val _events = MutableSharedFlow<SessionEvent>()
    override val events: SharedFlow<SessionEvent> = _events
    var connectCalled = false
    override suspend fun connect(device: PairedDevice) {
        connectCalled = true
        _state.value = ConnectionState.Connected
    }
    override suspend fun disconnect() { _state.value = ConnectionState.Disconnected }
    override suspend fun reconnect() {}
    override fun isConnected(): Boolean = _state.value == ConnectionState.Connected
    override fun currentCapabilities(): DeviceCapabilities? = null
    override fun currentEndpoint(): String? = null
    override fun currentAccessToken(): String? = null
    override fun onForeground() {}
    override fun onBackground() {}
}

class FakePairRepository : PairRepository {
    override suspend fun resolveEndpoint(endpoint: String) {}
    override suspend fun performTlsHandshake(endpoint: String) = "AA:BB:CC"
    override suspend fun authenticate(endpoint: String, bindToken: String) = "token" to "refresh"
    override suspend fun bind(deviceId: String, deviceName: String, endpoint: String, fingerprint: String, accessToken: String, refreshToken: String) =
        PairedDevice(deviceId, deviceName, endpoint, fingerprint, lastConnectedAt = 0L)
    override suspend fun negotiateCapabilities(endpoint: String, accessToken: String) = DeviceCapabilities()
    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {}
    override suspend fun getTrustedFingerprint(deviceId: String): String? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class PairViewModelTest {

    private data class TestHarness(
        val vm: PairViewModel,
        val deviceStorage: FakeDeviceStorage,
        val connectionStateManager: ConnectionStateManager,
        val sessionManager: FakeSessionManager,
        val testScope: TestScope
    )

    private fun createViewModel(
        wifiConnector: WifiDirectConnector = WifiDirectConnector()
    ): TestHarness = createHarness(wifiConnector)

    private fun createHarness(
        wifiConnector: WifiDirectConnector = WifiDirectConnector()
    ): TestHarness {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val parseQr = ParseQrPayloadUseCase(currentTimeSeconds = { 1_700_000_000L })
        val pairRepo = FakePairRepository()
        val pairStateMachine = PairStateMachine(pairRepo)
        val connectionStateManager = ConnectionStateManager()
        val deviceStorage = FakeDeviceStorage()
        val sessionManager = FakeSessionManager()

        val vm = PairViewModel(
            parseQrPayload = parseQr,
            pairStateMachine = pairStateMachine,
            connectionStateManager = connectionStateManager,
            deviceStorage = deviceStorage,
            wifiDirectConnector = wifiConnector,
            sessionManager = sessionManager,
            externalScope = testScope
        )
        testScope.advanceUntilIdle()

        return TestHarness(vm, deviceStorage, connectionStateManager, sessionManager, testScope)
    }

    @Test
    fun initialState_hasDefaults() {
        val harness = createViewModel()
        val state = harness.vm.state.value

        assertEquals("", state.manualEndpoint)
        assertEquals("", state.manualBindToken)
        assertEquals("", state.manualSsid)
        assertEquals("", state.manualWifiPassword)
        assertNull(state.lastError)
        assertEquals(false, state.isPairing)
        assertEquals(false, state.isScanning)
        assertEquals(false, state.isWifiDirectPairing)
        assertNull(state.showRemoveDeviceDialog)
    }

    // --- QR scanning ---

    @Test
    fun sonyQrCode_startsWifiDirectPairing() {
        val connector = WifiDirectConnector()
        val harness = createViewModel(wifiConnector = connector)
        harness.vm.onEvent(PairEvent.QrCodeScanned("W01:S:1YE1;P:KN9bWfc9;C:ILCE-7M3;M:D8106828244D;"))
        harness.testScope.advanceUntilIdle()

        val state = harness.vm.state.value
        assertNull(state.lastError)
        assertTrue(state.showModePicker)
    }

    @Test
    fun standardWifiQr_startsWifiDirectPairing() {
        val connector = WifiDirectConnector()
        val harness = createViewModel(wifiConnector = connector)
        harness.vm.onEvent(PairEvent.QrCodeScanned("WIFI:T:WPA;S:CameraNetwork;P:password123;;"))
        harness.testScope.advanceUntilIdle()

        val state = harness.vm.state.value
        assertNull(state.lastError)
        assertTrue(state.showModePicker)
    }

    @Test
    fun wifiQr_connectionFailed_showsError() {
        val connector = WifiDirectConnector()
        connector.connectResult = WifiDirectResult.Failed("No network")
        val harness = createViewModel(wifiConnector = connector)
        harness.vm.onEvent(PairEvent.QrCodeScanned("WIFI:T:WPA;S:CameraNetwork;P:pass;;"))
        harness.testScope.advanceUntilIdle()

        val state = harness.vm.state.value
        assertTrue(state.lastError?.contains("failed") == true)
    }

    @Test
    fun unrecognizedQrCode_showsError() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.QrCodeScanned("https://example.com"))

        val error = harness.vm.state.value.lastError
        assertTrue(error != null)
        assertTrue(error.contains("not a recognized"))
    }

    @Test
    fun expiredQrCode_showsExpiredMessage() {
        val harness = createViewModel()
        val json = """{"v":1,"deviceId":"dev","deviceName":"Box","endpoint":"https://h","fingerprint":"A","bindToken":"t","expiresAt":1}"""
        harness.vm.onEvent(PairEvent.QrCodeScanned(json))

        val error = harness.vm.state.value.lastError
        assertTrue(error != null)
        assertTrue(error.contains("expired"))
    }

    @Test
    fun unsupportedVersionQrCode_showsUpdateMessage() {
        val harness = createViewModel()
        val json = """{"v":99,"deviceId":"dev","deviceName":"Box","endpoint":"https://h","fingerprint":"A","bindToken":"t","expiresAt":9999999999}"""
        harness.vm.onEvent(PairEvent.QrCodeScanned(json))

        val error = harness.vm.state.value.lastError
        assertTrue(error != null)
        assertTrue(error.contains("version"))
    }

    // --- Manual entry ---

    @Test
    fun manualEndpointChanged_updatesState() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ManualEndpointChanged("https://192.168.1.10:8443"))

        assertEquals("https://192.168.1.10:8443", harness.vm.state.value.manualEndpoint)
    }

    @Test
    fun manualTokenChanged_updatesState() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ManualTokenChanged("my-token"))

        assertEquals("my-token", harness.vm.state.value.manualBindToken)
    }

    @Test
    fun manualSsidChanged_updatesState() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ManualSsidChanged("DIRECT-1234:Camera"))

        assertEquals("DIRECT-1234:Camera", harness.vm.state.value.manualSsid)
    }

    @Test
    fun manualWifiPasswordChanged_updatesState() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ManualWifiPasswordChanged("secret123"))

        assertEquals("secret123", harness.vm.state.value.manualWifiPassword)
    }

    @Test
    fun connectManual_emptyEndpoint_showsError() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ToggleAdvancedManual)
        harness.vm.onEvent(PairEvent.ConnectManual)

        assertTrue(harness.vm.state.value.lastError?.contains("endpoint") == true)
    }

    @Test
    fun connectManual_emptyToken_showsError() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ManualEndpointChanged("https://host"))
        harness.vm.onEvent(PairEvent.ConnectManual)

        assertTrue(harness.vm.state.value.lastError?.contains("bind token") == true)
    }

    @Test
    fun connectWifiDirect_emptySsid_showsError() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ConnectWifiDirect)

        assertTrue(harness.vm.state.value.lastError?.contains("SSID") == true)
    }

    @Test
    fun connectWifiDirect_withSsid_startsWifiPairing() {
        val connector = WifiDirectConnector()
        val harness = createViewModel(wifiConnector = connector)
        harness.vm.onEvent(PairEvent.ManualSsidChanged("DIRECT-1234:Camera"))
        harness.vm.onEvent(PairEvent.ManualWifiPasswordChanged("pass"))
        harness.vm.onEvent(PairEvent.ConnectWifiDirect)
        harness.testScope.advanceUntilIdle()

        val state = harness.vm.state.value
        assertTrue(state.showModePicker)
    }

    @Test
    fun toggleTokenVisibility_togglesState() {
        val harness = createViewModel()
        assertEquals(false, harness.vm.state.value.isTokenVisible)

        harness.vm.onEvent(PairEvent.ToggleTokenVisibility)
        assertEquals(true, harness.vm.state.value.isTokenVisible)

        harness.vm.onEvent(PairEvent.ToggleTokenVisibility)
        assertEquals(false, harness.vm.state.value.isTokenVisible)
    }

    @Test
    fun dismissError_clearsLastError() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.QrCodeScanned("junk"))
        assertTrue(harness.vm.state.value.lastError != null)

        harness.vm.onEvent(PairEvent.DismissError)
        assertNull(harness.vm.state.value.lastError)
    }

    @Test
    fun dismissScanner_stopsScanning() {
        val harness = createViewModel()
        harness.vm.onCameraPermissionGranted()
        assertTrue(harness.vm.state.value.isScanning)

        harness.vm.onEvent(PairEvent.DismissScanner)
        assertEquals(false, harness.vm.state.value.isScanning)
    }

    // --- Camera card actions ---

    @Test
    fun renameDevice_updatesCustomName() {
        val harness = createHarness()
        val device = PairedDevice(
            deviceId = "test-1",
            deviceName = "Sony Camera",
            endpoint = "http://192.168.1.1",
            fingerprint = ""
        )
        harness.deviceStorage.devices.add(device)
        harness.vm.onEvent(PairEvent.RenameDevice("test-1", "My Sony A7"))
        harness.testScope.advanceUntilIdle()

        val updated = harness.deviceStorage.devices.find { it.deviceId == "test-1" }
        assertEquals("My Sony A7", updated?.customName)
        assertEquals("My Sony A7", updated?.displayName)
    }

    @Test
    fun renameDevice_blankName_clearsCustomName() {
        val harness = createHarness()
        val device = PairedDevice(
            deviceId = "test-1",
            deviceName = "Sony Camera",
            endpoint = "http://192.168.1.1",
            fingerprint = "",
            customName = "Old Name"
        )
        harness.deviceStorage.devices.add(device)
        harness.vm.onEvent(PairEvent.RenameDevice("test-1", "  "))
        harness.testScope.advanceUntilIdle()

        val updated = harness.deviceStorage.devices.find { it.deviceId == "test-1" }
        assertNull(updated?.customName)
        assertEquals("Sony Camera", updated?.displayName)
    }

    @Test
    fun requestRemoveDevice_showsDialog() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.RequestRemoveDevice("device-123"))

        assertEquals("device-123", harness.vm.state.value.showRemoveDeviceDialog)
    }

    @Test
    fun dismissRemoveDialog_clearsState() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.RequestRemoveDevice("device-123"))
        harness.vm.onEvent(PairEvent.DismissRemoveDialog)

        assertNull(harness.vm.state.value.showRemoveDeviceDialog)
    }

    @Test
    fun confirmRemoveDevice_removesFromStorage() {
        val harness = createHarness()
        val device = PairedDevice(
            deviceId = "test-1",
            deviceName = "Sony Camera",
            endpoint = "http://192.168.1.1",
            fingerprint = ""
        )
        harness.deviceStorage.devices.add(device)
        harness.vm.onEvent(PairEvent.RequestRemoveDevice("test-1"))
        harness.vm.onEvent(PairEvent.ConfirmRemoveDevice)
        harness.testScope.advanceUntilIdle()

        assertTrue(harness.deviceStorage.devices.none { it.deviceId == "test-1" })
        assertNull(harness.vm.state.value.showRemoveDeviceDialog)
    }

    @Test
    fun quickReconnect_callsConnect() {
        val harness = createHarness()
        val device = PairedDevice(
            deviceId = "test-1",
            deviceName = "Sony Camera",
            endpoint = "http://192.168.1.1",
            fingerprint = "",
            wifiSsid = "DIRECT-1234:ILCE-7M3"
        )
        harness.deviceStorage.devices.add(device)
        harness.vm.onEvent(PairEvent.QuickReconnect("test-1"))
        harness.testScope.advanceUntilIdle()

        val state = harness.vm.state.value
        assertTrue(state.showModePicker || harness.sessionManager.connectCalled)
    }
}
