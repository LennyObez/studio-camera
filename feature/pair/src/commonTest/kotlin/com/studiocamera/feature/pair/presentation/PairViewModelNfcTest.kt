package com.studiocamera.feature.pair.presentation

import com.studiocamera.core.common.platform.WifiDirectConnector
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

@OptIn(ExperimentalCoroutinesApi::class)
class PairViewModelNfcTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PairViewModel {
        val parseQr = ParseQrPayloadUseCase(currentTimeSeconds = { 1_700_000_000L })
        val pairRepo = object : PairRepository {
            override suspend fun resolveEndpoint(endpoint: String) {}
            override suspend fun performTlsHandshake(endpoint: String) = "AA:BB:CC"
            override suspend fun authenticate(endpoint: String, bindToken: String) = "token" to "refresh"
            override suspend fun bind(deviceId: String, deviceName: String, endpoint: String, fingerprint: String, accessToken: String, refreshToken: String) =
                PairedDevice(deviceId, deviceName, endpoint, fingerprint, lastConnectedAt = 0L)
            override suspend fun negotiateCapabilities(endpoint: String, accessToken: String) = DeviceCapabilities()
            override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {}
            override suspend fun getTrustedFingerprint(deviceId: String): String? = null
        }
        val pairStateMachine = PairStateMachine(pairRepo)
        val connectionStateManager = ConnectionStateManager()
        val deviceStorage = object : DeviceStorageRepository {
            val devices = mutableListOf<PairedDevice>()
            override suspend fun savePairedDevice(device: PairedDevice) { devices.add(device) }
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
        val wifiDirectConnector = WifiDirectConnector()

        val sessionManager = object : SessionManager {
            private val _state = MutableStateFlow(ConnectionState.Disconnected)
            override val state: StateFlow<ConnectionState> = _state
            override val events: SharedFlow<SessionEvent> = MutableSharedFlow()
            override suspend fun connect(device: PairedDevice) { _state.value = ConnectionState.Connected }
            override suspend fun disconnect() { _state.value = ConnectionState.Disconnected }
            override suspend fun reconnect() {}
            override fun isConnected() = _state.value == ConnectionState.Connected
            override fun currentCapabilities(): DeviceCapabilities? = null
            override fun currentEndpoint(): String? = null
            override fun currentAccessToken(): String? = null
            override fun onForeground() {}
            override fun onBackground() {}
        }

        return PairViewModel(
            parseQrPayload = parseQr,
            pairStateMachine = pairStateMachine,
            connectionStateManager = connectionStateManager,
            deviceStorage = deviceStorage,
            wifiDirectConnector = wifiDirectConnector,
            sessionManager = sessionManager
        ).also { awaitDefault() } // let init coroutines on Dispatchers.Default settle
    }

    @Test
    fun scanNfc_setsNfcScanningTrue() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ScanNfc)
        assertTrue(vm.state.value.isNfcScanning)
    }

    @Test
    fun dismissNfcScanner_setsNfcScanningFalse() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ScanNfc)
        assertTrue(vm.state.value.isNfcScanning)

        vm.onEvent(PairEvent.DismissNfcScanner)
        assertEquals(false, vm.state.value.isNfcScanning)
    }

    @Test
    fun nfcTagRead_validJson_startsPairing() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ScanNfc)
        val json = """{"v":1,"deviceId":"nfc-dev","deviceName":"NFC Box","endpoint":"https://10.0.0.1:8443","fingerprint":"AB:CD","bindToken":"tok","expiresAt":9999999999}"""
        vm.onEvent(PairEvent.NfcTagRead(json))

        assertEquals(false, vm.state.value.isNfcScanning)
        assertTrue(vm.state.value.isPairing)
        assertNull(vm.state.value.lastError)
    }

    @Test
    fun nfcTagRead_invalidPayload_showsError() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.ScanNfc)
        vm.onEvent(PairEvent.NfcTagRead("not valid json at all"))

        assertEquals(false, vm.state.value.isNfcScanning)
        assertTrue(vm.state.value.lastError != null)
    }

    @Test
    fun nfcTagRead_expiredPayload_showsError() {
        val vm = createViewModel()
        val json = """{"v":1,"deviceId":"dev","deviceName":"Box","endpoint":"https://h","fingerprint":"A","bindToken":"t","expiresAt":1}"""
        vm.onEvent(PairEvent.NfcTagRead(json))

        assertTrue(vm.state.value.lastError?.contains("expired") == true)
    }

    @Test
    fun nfcTagRead_wifiFormat_startsWifiDirectPairing() {
        val vm = createViewModel()
        vm.onEvent(PairEvent.NfcTagRead("WIFI:T:WPA;S:CameraNet;P:pass123;;"))
        awaitDefault()

        assertEquals(false, vm.state.value.isNfcScanning)
        // JVM WifiDirectConnector returns Connected by default → shows mode picker
        assertTrue(vm.state.value.showModePicker)
        assertNull(vm.state.value.lastError)
    }
}
