package com.studiocamera.feature.settings.presentation

import com.studiocamera.core.domain.model.AppSettings
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.model.ThemeMode
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

    override suspend fun resetToDefaults() {
        _settings.value = AppSettings()
    }
}

class FakeDeviceStorage : DeviceStorageRepository {
    val devices = mutableListOf<PairedDevice>()
    private val wifiPasswords = mutableMapOf<String, String>()
    override suspend fun savePairedDevice(device: PairedDevice) { devices.add(device) }
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private data class TestHarness(
        val vm: SettingsViewModel,
        val settingsRepo: FakeSettingsRepository,
        val deviceStorage: FakeDeviceStorage,
        val testScope: TestScope
    )

    private fun createHarness(
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        deviceStorage: FakeDeviceStorage = FakeDeviceStorage()
    ): TestHarness {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val vm = SettingsViewModel(
            settingsRepository = settingsRepo,
            deviceStorage = deviceStorage,
            externalScope = testScope
        )
        testScope.advanceUntilIdle()
        return TestHarness(vm, settingsRepo, deviceStorage, testScope)
    }

    @Test
    fun initialState_hasDefaults() {
        val harness = createHarness()
        val state = harness.vm.state.value
        assertEquals(ThemeMode.System, state.settings.themeMode)
        assertEquals(true, state.settings.autoReconnect)
        assertEquals(true, state.settings.keepScreenOn)
    }

    @Test
    fun updateTheme_updatesSettings() {
        val harness = createHarness()

        harness.vm.onEvent(SettingsEvent.UpdateTheme(ThemeMode.Dark))
        harness.testScope.advanceUntilIdle()
        assertEquals(ThemeMode.Dark, harness.settingsRepo.settings.value.themeMode)
    }

    @Test
    fun updateAutoReconnect_updatesSettings() {
        val harness = createHarness()

        harness.vm.onEvent(SettingsEvent.UpdateAutoReconnect(false))
        harness.testScope.advanceUntilIdle()
        assertEquals(false, harness.settingsRepo.settings.value.autoReconnect)
    }

    @Test
    fun forgetDevice_showsDialog() {
        val harness = createHarness()
        harness.vm.onEvent(SettingsEvent.ForgetDevice("dev-1"))
        assertEquals("dev-1", harness.vm.state.value.showForgetDeviceDialog)
    }

    @Test
    fun confirmForgetDevice_removesDevice() {
        val deviceStorage = FakeDeviceStorage()
        deviceStorage.devices.add(
            PairedDevice("dev-1", "Test Device", "https://host", "AA:BB", lastConnectedAt = 0L)
        )
        val harness = createHarness(deviceStorage = deviceStorage)

        harness.vm.onEvent(SettingsEvent.ForgetDevice("dev-1"))
        harness.vm.onEvent(SettingsEvent.ConfirmForgetDevice)
        harness.testScope.advanceUntilIdle()

        assertEquals(true, harness.deviceStorage.devices.isEmpty())
    }

    @Test
    fun resetToDefaults_restoresDefaults() {
        val harness = createHarness()

        harness.vm.onEvent(SettingsEvent.UpdateTheme(ThemeMode.Dark))
        harness.testScope.advanceUntilIdle()
        harness.vm.onEvent(SettingsEvent.ResetToDefaults)
        harness.testScope.advanceUntilIdle()

        assertEquals(ThemeMode.System, harness.settingsRepo.settings.value.themeMode)
    }
}
