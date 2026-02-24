package com.studiocamera.feature.pair.presentation

import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.usecase.ParseQrPayloadUseCase
import com.studiocamera.feature.pair.domain.PairStateMachine
import com.studiocamera.core.domain.repository.PairRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairViewModelNfcTest {

    private data class NfcTestHarness(
        val vm: PairViewModel,
        val testScope: TestScope
    )

    private fun createViewModel(): NfcTestHarness {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val parseQr = ParseQrPayloadUseCase(currentTimeSeconds = { 1_700_000_000L })
        val pairRepo = FakePairRepository()
        val pairStateMachine = PairStateMachine(pairRepo)
        val connectionStateManager = ConnectionStateManager()
        val deviceStorage = FakeDeviceStorage()
        val wifiDirectConnector = WifiDirectConnector()
        val sessionManager = FakeSessionManager()
        val wifiDirectPairingManager = WifiDirectPairingManager(
            wifiDirectConnector = wifiDirectConnector,
            deviceStorage = deviceStorage,
            sessionManager = sessionManager
        )
        val deviceListManager = DeviceListManager(
            deviceStorage = deviceStorage,
            connectionStateManager = connectionStateManager,
            wifiDirectConnector = wifiDirectConnector,
            sessionManager = sessionManager
        )

        val vm = PairViewModel(
            parseQrPayload = parseQr,
            pairStateMachine = pairStateMachine,
            connectionStateManager = connectionStateManager,
            wifiDirectConnector = wifiDirectConnector,
            sessionManager = sessionManager,
            wifiDirectPairingManager = wifiDirectPairingManager,
            deviceListManager = deviceListManager,
            externalScope = testScope
        )
        testScope.advanceUntilIdle()

        return NfcTestHarness(vm, testScope)
    }

    @Test
    fun scanNfc_setsNfcScanningTrue() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ScanNfc)
        assertTrue(harness.vm.state.value.isNfcScanning)
    }

    @Test
    fun dismissNfcScanner_setsNfcScanningFalse() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ScanNfc)
        assertTrue(harness.vm.state.value.isNfcScanning)

        harness.vm.onEvent(PairEvent.DismissNfcScanner)
        assertEquals(false, harness.vm.state.value.isNfcScanning)
    }

    @Test
    fun nfcTagRead_validJson_startsPairing() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ScanNfc)
        val json = """{"v":1,"deviceId":"nfc-dev","deviceName":"NFC Box","endpoint":"https://10.0.0.1:8443","fingerprint":"AB:CD","bindToken":"tok","expiresAt":9999999999}"""
        harness.vm.onEvent(PairEvent.NfcTagRead(json))

        assertEquals(false, harness.vm.state.value.isNfcScanning)
        assertTrue(harness.vm.state.value.isPairing)
        assertNull(harness.vm.state.value.lastError)
    }

    @Test
    fun nfcTagRead_invalidPayload_showsError() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.ScanNfc)
        harness.vm.onEvent(PairEvent.NfcTagRead("not valid json at all"))

        assertEquals(false, harness.vm.state.value.isNfcScanning)
        assertTrue(harness.vm.state.value.lastError != null)
    }

    @Test
    fun nfcTagRead_expiredPayload_showsError() {
        val harness = createViewModel()
        val json = """{"v":1,"deviceId":"dev","deviceName":"Box","endpoint":"https://h","fingerprint":"A","bindToken":"t","expiresAt":1}"""
        harness.vm.onEvent(PairEvent.NfcTagRead(json))

        assertTrue(harness.vm.state.value.lastError?.contains("expired") == true)
    }

    @Test
    fun nfcTagRead_wifiFormat_startsWifiDirectPairing() {
        val harness = createViewModel()
        harness.vm.onEvent(PairEvent.NfcTagRead("WIFI:T:WPA;S:CameraNet;P:pass123;;"))
        harness.testScope.advanceUntilIdle()

        assertEquals(false, harness.vm.state.value.isNfcScanning)
        assertTrue(harness.vm.state.value.showModePicker)
        assertNull(harness.vm.state.value.lastError)
    }
}
