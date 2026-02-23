package com.studiocamera.feature.settings.presentation

import com.studiocamera.core.domain.model.AppSettings
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.ThemeMode
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val pairedDevices: List<PairedDevice> = emptyList(),
    val appVersion: String = "1.0.0",
    val showClearDataDialog: Boolean = false,
    val showForgetDeviceDialog: String? = null, // deviceId
    val devTapCount: Int = 0,
    val showDeveloperModeSnackbar: Boolean = false,
    val showFeedbackSheet: Boolean = false
)

sealed class SettingsEvent {
    data class UpdateTheme(val mode: ThemeMode) : SettingsEvent()
    data class UpdateAutoReconnect(val enabled: Boolean) : SettingsEvent()
    data class UpdateKeepScreenOn(val enabled: Boolean) : SettingsEvent()
    data class UpdateShowBatteryWarnings(val enabled: Boolean) : SettingsEvent()
    data class UpdateDefaultGrid(val enabled: Boolean) : SettingsEvent()
    data class UpdateDefaultSafeZone(val enabled: Boolean) : SettingsEvent()
    data class UpdateDeveloperMode(val enabled: Boolean) : SettingsEvent()
    data class ForgetDevice(val deviceId: String) : SettingsEvent()
    data object ConfirmForgetDevice : SettingsEvent()
    data object DismissForgetDevice : SettingsEvent()
    data object ShowClearDataDialog : SettingsEvent()
    data object ConfirmClearData : SettingsEvent()
    data object DismissClearData : SettingsEvent()
    data object ResetToDefaults : SettingsEvent()
    data object TapVersionString : SettingsEvent()
    data object DismissDeveloperSnackbar : SettingsEvent()
    data object ShowFeedback : SettingsEvent()
    data object DismissFeedback : SettingsEvent()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val deviceStorage: DeviceStorageRepository,
    appVersion: String = "1.0.0",
    externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(SettingsUiState(appVersion = appVersion))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observeSettings()
        loadPairedDevices()
    }

    fun destroy() {
        scope.cancel()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.UpdateTheme -> updateSetting { it.copy(themeMode = event.mode) }
            is SettingsEvent.UpdateAutoReconnect -> updateSetting { it.copy(autoReconnect = event.enabled) }
            is SettingsEvent.UpdateKeepScreenOn -> updateSetting { it.copy(keepScreenOn = event.enabled) }
            is SettingsEvent.UpdateShowBatteryWarnings -> updateSetting { it.copy(showBatteryWarnings = event.enabled) }
            is SettingsEvent.UpdateDefaultGrid -> updateSetting { it.copy(defaultGrid = event.enabled) }
            is SettingsEvent.UpdateDefaultSafeZone -> updateSetting { it.copy(defaultSafeZone = event.enabled) }
            is SettingsEvent.UpdateDeveloperMode -> updateSetting { it.copy(developerMode = event.enabled) }
            is SettingsEvent.ForgetDevice -> {
                _state.update { it.copy(showForgetDeviceDialog = event.deviceId) }
            }
            SettingsEvent.ConfirmForgetDevice -> {
                val deviceId = _state.value.showForgetDeviceDialog ?: return
                _state.update { it.copy(showForgetDeviceDialog = null) }
                scope.launch {
                    try {
                        deviceStorage.removePairedDevice(deviceId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Storage error — device list will be stale but not crash
                    }
                    loadPairedDevices()
                }
            }
            SettingsEvent.DismissForgetDevice -> {
                _state.update { it.copy(showForgetDeviceDialog = null) }
            }
            SettingsEvent.ShowClearDataDialog -> {
                _state.update { it.copy(showClearDataDialog = true) }
            }
            SettingsEvent.ConfirmClearData -> {
                _state.update { it.copy(showClearDataDialog = false) }
                scope.launch {
                    try {
                        deviceStorage.clearAll()
                        settingsRepository.resetToDefaults()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Storage error — UI already dismissed dialog
                    }
                    loadPairedDevices()
                }
            }
            SettingsEvent.DismissClearData -> {
                _state.update { it.copy(showClearDataDialog = false) }
            }
            SettingsEvent.ResetToDefaults -> {
                scope.launch {
                    try {
                        settingsRepository.resetToDefaults()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Settings reset failed silently
                    }
                }
            }
            SettingsEvent.TapVersionString -> {
                val newCount = _state.value.devTapCount + 1
                if (newCount >= 7 && !_state.value.settings.developerMode) {
                    updateSetting { it.copy(developerMode = true) }
                    _state.update { it.copy(
                        devTapCount = 0,
                        showDeveloperModeSnackbar = true
                    ) }
                } else {
                    _state.update { it.copy(devTapCount = newCount) }
                }
            }
            SettingsEvent.DismissDeveloperSnackbar -> {
                _state.update { it.copy(showDeveloperModeSnackbar = false) }
            }
            SettingsEvent.ShowFeedback -> {
                _state.update { it.copy(showFeedbackSheet = true) }
            }
            SettingsEvent.DismissFeedback -> {
                _state.update { it.copy(showFeedbackSheet = false) }
            }
        }
    }

    private fun updateSetting(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            try {
                settingsRepository.updateSettings(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Settings update failed — state will be stale until next observe
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    private fun loadPairedDevices() {
        scope.launch {
            try {
                val devices = deviceStorage.getPairedDevices()
                _state.update { it.copy(pairedDevices = devices) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Storage error — keep existing device list
            }
        }
    }
}
