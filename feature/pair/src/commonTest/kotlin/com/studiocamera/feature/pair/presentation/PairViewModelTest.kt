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
import com.studiocamera.feature.pair.domain.PairProgress
import com.studiocamera.feature.pair.domain.PairStateMachine
import com.studiocamera.core.domain.repository.PairRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Let Dispatchers.Default coroutines settle (all fakes are in-memory). */
private fun awaitDefault() = Thread.sleep(200)

class FakeDeviceStorage : DeviceStorageRepository {
    val devices = mutableListOf<PairedDevice>()
    override suspend fun savePairedDevice(device: PairedDevice) {
        devices.removeAll { it.deviceId == device.deviceId }
        devices.add(device)
    }
    override suspend fun getPairedDevices(): List<PairedDevice> = devices.toList()
    override suspend fun getPairedDevice(deviceId: String) = devices.find { it.deviceId == deviceId }
    override suspend fun removePairedDevice(deviceId: String) { devices.removeAll { it.deviceId == deviceId } }
    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {}
    override suspend fun getTrustedFingerprint(deviceId: String): String? = null
    override suspend fun saveSessionInfo(info: SessionInfo) {}
    override suspend fun getSessionInfo(deviceId: String): SessionInfo? = null
    override suspend fun clearSessionInfo(deviceId: String) {}
    override suspend fun clearAll() { devices.clear() }
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

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private data class TestHarness(
        val vm: PairViewModel,
        val deviceStorage: FakeDeviceStorage,
        val connectionStateManager: ConnectionStateManager,
        val sessionManager: FakeSessionManager
    )

    private fun createViewModel(
        wifiConnector: WifiDirectConnector = WifiDirectConnector()
    ): PairViewModel = createHarness(wifiConnector).vm

    private fun createHarness(
        wifiConnector: WifiDirectConnector = WifiDirectConnector()
    ): TestHarness {
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
            sessionManager = sessionManager
        ).also { awaitDefault() }

        return TestHarness(vm, deviceStorage, connectionStateManager, sessionManager)
    }

    @Test
    fun initialState_hasDefaults() {
        val vm = createViewModel()
        val state = vm.state.value

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
        val vm = createViewModel(wifiConnector = connector)
        vm.onEvent(PairEvent.QrCodeScanned("W01:S:1YE1;P:KN9bWfc9;C:ILCE-7M3;M:D8106828244D;"))
        awaitDefault()

        // Sony QR code now starts Wi-Fi Direct pairing instead of showing error
        // The connector returns Connected by default on JVM
        val state = vm.state.value
        assertNull(state.lastError)
        // Should show mode picker after successful connection
        assertTrue(state.showModePicker)
    }

    @Test
    fun standardWifiQr_startsWifiDirectPairing() {
        val connector = WifiDirectConnector()
        val vm = createViewModel(wifiConnector = connector)
        vm.onEvent(PairEvent.QrCodeScanned("WIFI:T:WPA;S:CameraNetwork;P:password123;;"))
        awaitDefault()

        val state = vm.state.value
        assertNull(state.lastError)
        assertTrue(state.showModePicker)
    }

    @Test
    fun wifiQr_connectionFailed_showsError() {
        val connector = WifiDirectConnector()
        connector.connectResult = WifiDirectResult.Failed("No network")
        val vm = createViewModel(wifiConnector = connector)
        vm.onEvent(PairEvent.QrCodeScanned("WIFI:T:WPA;S:CameraNetwork;P:pass;;"))
        awaitDefault()

        val state = vm.state.value
        assertTrue(state.lastError?.contains("failed") == true)
    }

    @Test
    fun unrecognizedQrCode_showsError() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.QrCodeScanned("https://example.com"))

        val error = vm.state.value.lastError
        assertTrue(error != null)
        assertTrue(error.contains("not a recognized"))
    }

    @Test
    fun expiredQrCode_showsExpiredMessage() {
        val vm = createViewModel()
        val json = """{"v":1,"deviceId":"dev","deviceName":"Box","endpoint":"https://h","fingerprint":"A","bindToken":"t","expiresAt":1}"""
        vm.onEvent(PairEvent.QrCodeScanned(json))

        val error = vm.state.value.lastError
        assertTrue(error != null)
        assertTrue(error.contains("expired"))
    }

    @Test
    fun unsupportedVersionQrCode_showsUpdateMessage() {
        val vm = createViewModel()
        val json = """{"v":99,"deviceId":"dev","deviceName":"Box","endpoint":"https://h","fingerprint":"A","bindToken":"t","expiresAt":9999999999}"""
        vm.onEvent(PairEvent.QrCodeScanned(json))

        val error = vm.state.value.lastError
        assertTrue(error != null)
        assertTrue(error.contains("version"))
    }

    // --- Manual entry ---

    @Test
    fun manualEndpointChanged_updatesState() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ManualEndpointChanged("https://192.168.1.10:8443"))

        assertEquals("https://192.168.1.10:8443", vm.state.value.manualEndpoint)
    }

    @Test
    fun manualTokenChanged_updatesState() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ManualTokenChanged("my-token"))

        assertEquals("my-token", vm.state.value.manualBindToken)
    }

    @Test
    fun manualSsidChanged_updatesState() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ManualSsidChanged("DIRECT-1234:Camera"))

        assertEquals("DIRECT-1234:Camera", vm.state.value.manualSsid)
    }

    @Test
    fun manualWifiPasswordChanged_updatesState() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ManualWifiPasswordChanged("secret123"))

        assertEquals("secret123", vm.state.value.manualWifiPassword)
    }

    @Test
    fun connectManual_emptyEndpoint_showsError() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ToggleAdvancedManual) // show advanced
        vm.onEvent(PairEvent.ConnectManual)

        assertTrue(vm.state.value.lastError?.contains("endpoint") == true)
    }

    @Test
    fun connectManual_emptyToken_showsError() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ManualEndpointChanged("https://host"))
        vm.onEvent(PairEvent.ConnectManual)

        assertTrue(vm.state.value.lastError?.contains("bind token") == true)
    }

    @Test
    fun connectWifiDirect_emptySsid_showsError() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ConnectWifiDirect)

        assertTrue(vm.state.value.lastError?.contains("SSID") == true)
    }

    @Test
    fun connectWifiDirect_withSsid_startsWifiPairing() {
        val connector = WifiDirectConnector()
        val vm = createViewModel(wifiConnector = connector)
        vm.onEvent(PairEvent.ManualSsidChanged("DIRECT-1234:Camera"))
        vm.onEvent(PairEvent.ManualWifiPasswordChanged("pass"))
        vm.onEvent(PairEvent.ConnectWifiDirect)
        awaitDefault()

        // JVM connector returns Connected by default
        val state = vm.state.value
        assertTrue(state.showModePicker)
    }

    @Test
    fun toggleTokenVisibility_togglesState() {
        val vm = createViewModel()
        assertEquals(false, vm.state.value.isTokenVisible)

        vm.onEvent(PairEvent.ToggleTokenVisibility)
        assertEquals(true, vm.state.value.isTokenVisible)

        vm.onEvent(PairEvent.ToggleTokenVisibility)
        assertEquals(false, vm.state.value.isTokenVisible)
    }

    @Test
    fun dismissError_clearsLastError() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.QrCodeScanned("junk"))
        assertTrue(vm.state.value.lastError != null)

        vm.onEvent(PairEvent.DismissError)
        assertNull(vm.state.value.lastError)
    }

    @Test
    fun dismissScanner_stopsScanning() {
        val vm = createViewModel()
        vm.onCameraPermissionGranted()
        assertTrue(vm.state.value.isScanning)

        vm.onEvent(PairEvent.DismissScanner)
        assertEquals(false, vm.state.value.isScanning)
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
        awaitDefault()

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
        awaitDefault()

        val updated = harness.deviceStorage.devices.find { it.deviceId == "test-1" }
        assertNull(updated?.customName)
        assertEquals("Sony Camera", updated?.displayName)
    }

    @Test
    fun requestRemoveDevice_showsDialog() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.RequestRemoveDevice("device-123"))

        assertEquals("device-123", vm.state.value.showRemoveDeviceDialog)
    }

    @Test
    fun dismissRemoveDialog_clearsState() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.RequestRemoveDevice("device-123"))
        vm.onEvent(PairEvent.DismissRemoveDialog)

        assertNull(vm.state.value.showRemoveDeviceDialog)
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
        awaitDefault()

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
        awaitDefault()

        // Wi-Fi Direct pairing should have been attempted
        val state = harness.vm.state.value
        // On JVM, WifiDirectConnector returns Connected by default
        assertTrue(state.showModePicker || harness.sessionManager.connectCalled)
    }
}
