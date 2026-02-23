package com.studiocamera.feature.camera.presentation

import com.studiocamera.core.common.MockModeManager
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.FlashMode
import com.studiocamera.core.domain.model.ImageFormat
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.feature.camera.presentation.overlay.GridType
import com.studiocamera.feature.camera.presentation.overlay.OverlayConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CameraViewState(
    val screenState: CameraScreenState = CameraScreenState(),
    val cameraState: CameraState = CameraState(),
    val isMockMode: Boolean = false,
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    val recordingSeconds: Int = 0,
    val focusTapPosition: Pair<Float, Float>? = null,
    val snackbarMessage: String? = null
)

class CameraViewModel(
    private val cameraRepository: CameraRepository,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val mockModeManager: MockModeManager,
    private val connectionStateManager: ConnectionStateManager,
    externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(CameraViewState())
    val state: StateFlow<CameraViewState> = _state.asStateFlow()

    val frameFlow: Flow<ByteArray> = cameraRepository.liveViewFrames()

    init {
        observeCameraState()
        observeSettings()
        observeCapabilities()
        observeMockMode()
    }

    fun destroy() {
        scope.cancel()
    }

    fun onCapture() {
        val current = _state.value
        if (current.screenState.timerSeconds > 0 && current.screenState.timerCountdown == null) {
            _state.update { it.copy(
                screenState = it.screenState.copy(timerCountdown = it.screenState.timerSeconds)
            ) }
            startTimerCountdown()
        } else {
            scope.launch { executeCapture() }
        }
    }

    fun onZoomChange(zoom: Float) {
        _state.update { it.copy(screenState = it.screenState.copy(zoomLevel = zoom)) }
    }

    fun onFocusTap(normalizedX: Float, normalizedY: Float, rawX: Float, rawY: Float) {
        _state.update { it.copy(focusTapPosition = rawX to rawY) }
        scope.launch { cameraRepository.tapToFocus(normalizedX, normalizedY) }
        // Auto-dismiss focus indicator
        scope.launch {
            delay(1500)
            _state.update { if (it.focusTapPosition == rawX to rawY) it.copy(focusTapPosition = null) else it }
        }
    }

    fun toggleRecording() {
        scope.launch {
            val cameraState = _state.value.cameraState
            if (cameraState.isRecording) {
                val result = cameraRepository.stopRecording()
                if (result is ApiResult.Success) {
                    showSnackbar("Recording saved")
                } else if (result is ApiResult.Error) {
                    showSnackbar("Failed: ${result.error.message}")
                }
            } else {
                val result = cameraRepository.startRecording()
                if (result is ApiResult.Error) {
                    showSnackbar("Failed: ${result.error.message}")
                }
            }
        }
    }

    fun updateScreenState(newState: CameraScreenState) {
        _state.update { it.copy(screenState = newState) }
    }

    fun updateOverlayConfig(config: OverlayConfig) {
        _state.update { it.copy(screenState = it.screenState.copy(overlayConfig = config)) }
    }

    fun onSelectorClick(selector: SelectorType) {
        _state.update { it.copy(screenState = it.screenState.copy(activeSelector = selector)) }
    }

    fun dismissSelector() {
        _state.update { it.copy(screenState = it.screenState.copy(activeSelector = null)) }
    }

    fun onShootModeChange(mode: String) {
        scope.launch { cameraRepository.setShootMode(mode) }
    }

    fun updateSettings(
        iso: Int? = null,
        shutterSpeed: String? = null,
        aperture: Float? = null,
        ev: Float? = null,
        flashMode: FlashMode? = null,
        imageFormat: ImageFormat? = null,
        videoResolution: String? = null,
        videoFps: Int? = null,
        whiteBalance: String? = null,
        exposureMode: String? = null,
        photoResolution: String? = null
    ) {
        scope.launch {
            cameraRepository.updateSettings(
                iso = iso, shutterSpeed = shutterSpeed, aperture = aperture, ev = ev,
                flashMode = flashMode, imageFormat = imageFormat,
                videoResolution = videoResolution, videoFps = videoFps,
                whiteBalance = whiteBalance, exposureMode = exposureMode,
                photoResolution = photoResolution
            )
        }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private suspend fun executeCapture() {
        val current = _state.value
        when (current.screenState.captureMode) {
            CaptureMode.Photo -> {
                if (current.cameraState.shootMode == "movie") {
                    showSnackbar("Cannot take photo in video mode")
                } else {
                    val result = cameraRepository.capturePhoto()
                    when (result) {
                        is ApiResult.Success -> showSnackbar("Photo captured")
                        is ApiResult.Error -> showSnackbar("Capture failed: ${result.error.message}")
                    }
                }
            }
            CaptureMode.Video -> {
                if (current.cameraState.isRecording) {
                    val result = cameraRepository.stopRecording()
                    when (result) {
                        is ApiResult.Success -> showSnackbar("Recording saved")
                        is ApiResult.Error -> showSnackbar("Failed: ${result.error.message}")
                    }
                } else {
                    val result = cameraRepository.startRecording()
                    if (result is ApiResult.Error) {
                        showSnackbar("Failed: ${result.error.message}")
                    }
                }
            }
        }
    }

    private fun startTimerCountdown() {
        scope.launch {
            while (isActive) {
                val countdown = _state.value.screenState.timerCountdown ?: break
                if (countdown <= 0) {
                    _state.update { it.copy(screenState = it.screenState.copy(timerCountdown = null)) }
                    executeCapture()
                    break
                }
                delay(1000)
                _state.update { it.copy(
                    screenState = it.screenState.copy(
                        timerCountdown = (it.screenState.timerCountdown ?: 0) - 1
                    )
                ) }
            }
        }
    }

    private fun showSnackbar(message: String) {
        _state.update { it.copy(snackbarMessage = message) }
    }

    private fun observeCameraState() {
        scope.launch {
            cameraRepository.cameraState.collect { cameraState ->
                _state.update { current ->
                    var screenState = current.screenState
                    // Sync shoot mode to UI
                    if (cameraState.valuesReported) {
                        val actualMode = if (cameraState.shootMode == "movie") CaptureMode.Video else CaptureMode.Photo
                        if (screenState.captureMode != actualMode) {
                            screenState = screenState.copy(captureMode = actualMode)
                        }
                    }
                    current.copy(cameraState = cameraState, screenState = screenState)
                }
                // Recording timer
                if (cameraState.isRecording) {
                    startRecordingTimer()
                } else {
                    _state.update { it.copy(recordingSeconds = 0) }
                }
            }
        }
    }

    private fun startRecordingTimer() {
        scope.launch {
            _state.update { it.copy(recordingSeconds = 0) }
            while (isActive && _state.value.cameraState.isRecording) {
                delay(1000)
                _state.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settings.collect { appSettings ->
                _state.update {
                    it.copy(
                        screenState = it.screenState.copy(
                            overlayConfig = it.screenState.overlayConfig.copy(
                                gridType = if (appSettings.defaultGrid) GridType.RuleOfThirds else GridType.None,
                                showSafeZone = appSettings.defaultSafeZone
                            )
                        )
                    )
                }
            }
        }
    }

    private fun observeCapabilities() {
        scope.launch {
            connectionStateManager.state.collect {
                _state.update { current ->
                    current.copy(capabilities = sessionManager.currentCapabilities() ?: DeviceCapabilities())
                }
            }
        }
    }

    private fun observeMockMode() {
        scope.launch {
            mockModeManager.isMockActive.collect { isMock ->
                _state.update { it.copy(isMockMode = isMock) }
            }
        }
    }
}
