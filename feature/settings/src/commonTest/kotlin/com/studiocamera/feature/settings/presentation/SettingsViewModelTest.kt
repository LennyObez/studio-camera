package com.studiocamera.feature.settings.presentation

import com.studiocamera.core.domain.model.AppSettings
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.model.ThemeMode
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Let Dispatchers.Default coroutines settle (all fakes are in-memory). */
private fun awaitDefault() = Thread.sleep(200)

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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        deviceStorage: FakeDeviceStorage = FakeDeviceStorage()
    ): SettingsViewModel {
        return SettingsViewModel(
            settingsRepository = settingsRepo,
            deviceStorage = deviceStorage
        ).also { awaitDefault() } // let init coroutines on Dispatchers.Default settle
    }

    @Test
    fun initialState_hasDefaults() {
        val vm = createViewModel()
        val state = vm.state.value
        assertEquals(ThemeMode.System, state.settings.themeMode)
        assertEquals(true, state.settings.autoReconnect)
        assertEquals(true, state.settings.keepScreenOn)
    }

    @Test
    fun updateTheme_updatesSettings() {
        val settingsRepo = FakeSettingsRepository()
        val vm = createViewModel(settingsRepo = settingsRepo)

        vm.onEvent(SettingsEvent.UpdateTheme(ThemeMode.Dark))
        awaitDefault()
        assertEquals(ThemeMode.Dark, settingsRepo.settings.value.themeMode)
    }

    @Test
    fun updateAutoReconnect_updatesSettings() {
        val settingsRepo = FakeSettingsRepository()
        val vm = createViewModel(settingsRepo = settingsRepo)

        vm.onEvent(SettingsEvent.UpdateAutoReconnect(false))
        awaitDefault()
        assertEquals(false, settingsRepo.settings.value.autoReconnect)
    }

    @Test
    fun forgetDevice_showsDialog() {
        val vm = createViewModel()
        vm.onEvent(SettingsEvent.ForgetDevice("dev-1"))
        assertEquals("dev-1", vm.state.value.showForgetDeviceDialog)
    }

    @Test
    fun confirmForgetDevice_removesDevice() {
        val deviceStorage = FakeDeviceStorage()
        deviceStorage.devices.add(
            PairedDevice("dev-1", "Test Device", "https://host", "AA:BB", lastConnectedAt = 0L)
        )
        val vm = createViewModel(deviceStorage = deviceStorage)

        vm.onEvent(SettingsEvent.ForgetDevice("dev-1"))
        vm.onEvent(SettingsEvent.ConfirmForgetDevice)
        awaitDefault()

        assertEquals(true, deviceStorage.devices.isEmpty())
    }

    @Test
    fun resetToDefaults_restoresDefaults() {
        val settingsRepo = FakeSettingsRepository()
        val vm = createViewModel(settingsRepo = settingsRepo)

        vm.onEvent(SettingsEvent.UpdateTheme(ThemeMode.Dark))
        awaitDefault()
        vm.onEvent(SettingsEvent.ResetToDefaults)
        awaitDefault()

        assertEquals(ThemeMode.System, settingsRepo.settings.value.themeMode)
    }
}
