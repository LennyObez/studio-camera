package com.studiocamera.feature.pair.presentation

import com.studiocamera.core.common.currentEpochSeconds
import com.studiocamera.core.common.platform.WifiDirectConnector
import com.studiocamera.core.common.platform.WifiDirectResult
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.ConnectionType
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.ParseResult
import com.studiocamera.core.domain.model.QrPayload
import com.studiocamera.core.domain.model.detectCameraBrand
import com.studiocamera.core.domain.model.formatCameraDisplayName
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.domain.usecase.ParseQrPayloadUseCase
import com.studiocamera.feature.pair.domain.PairProgress
import com.studiocamera.feature.pair.domain.PairStateMachine
import co.touchlab.kermit.Logger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PairUiState(
    val manualEndpoint: String = "",
    val manualBindToken: String = "",
    val manualSsid: String = "",
    val manualWifiPassword: String = "",
    val isTokenVisible: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isScanning: Boolean = false,
    val isNfcScanning: Boolean = false,
    val isPairing: Boolean = false,
    val isWifiDirectPairing: Boolean = false,
    val wifiDirectStep: WifiDirectStep = WifiDirectStep.Idle,
    val pairProgress: PairProgress = PairProgress(),
    val showModePicker: Boolean = false,
    val showTrustDialog: Boolean = false,
    val trustFingerprint: String = "",
    val pairedDevices: List<PairedDevice> = emptyList(),
    val lastError: String? = null,
    val ssidName: String? = null,
    val ssidUnavailableReason: String? = null,
    val wifiConnected: Boolean = false,
    val showAdvancedManual: Boolean = false,
    val showRemoveDeviceDialog: String? = null
)

enum class WifiDirectStep {
    Idle,
    Connecting,
    DetectingCamera,
    Saving,
    Initializing,
    Done,
    Failed
}

sealed class PairSideEffect {
    data class ShowSnackbar(val message: String) : PairSideEffect()
    data object NavigateToCamera : PairSideEffect()
    data object NavigateToMedia : PairSideEffect()
    data object OpenWifiSettings : PairSideEffect()
    data object OpenAppSettings : PairSideEffect()
    data object RequestCameraPermission : PairSideEffect()
    data object RequestNearbyWifiPermission : PairSideEffect()
    data object RequestLocationPermission : PairSideEffect()
    data object EnableNfcForegroundDispatch : PairSideEffect()
    data object DisableNfcForegroundDispatch : PairSideEffect()
}

sealed class PairEvent {
    data object ScanQrCode : PairEvent()
    data class QrCodeScanned(val rawPayload: String) : PairEvent()
    data object DismissScanner : PairEvent()
    data class ManualEndpointChanged(val value: String) : PairEvent()
    data class ManualTokenChanged(val value: String) : PairEvent()
    data class ManualSsidChanged(val value: String) : PairEvent()
    data class ManualWifiPasswordChanged(val value: String) : PairEvent()
    data object ToggleTokenVisibility : PairEvent()
    data object TogglePasswordVisibility : PairEvent()
    data object ConnectManual : PairEvent()
    data object ConnectWifiDirect : PairEvent()
    data object ToggleAdvancedManual : PairEvent()
    data class TrustConfirmed(val trusted: Boolean) : PairEvent()
    data object CancelPairing : PairEvent()
    data object RetryPairing : PairEvent()
    data object SelectLiveControl : PairEvent()
    data object SelectMediaBrowser : PairEvent()
    data object DismissModePicker : PairEvent()
    data object Disconnect : PairEvent()
    data object Reconnect : PairEvent()
    data object OpenWifiSettings : PairEvent()
    data object OpenAppSettings : PairEvent()
    data object DismissError : PairEvent()
    data object ScanNfc : PairEvent()
    data class NfcTagRead(val rawPayload: String) : PairEvent()
    data object DismissNfcScanner : PairEvent()
    data class RenameDevice(val deviceId: String, val newName: String) : PairEvent()
    data class RequestRemoveDevice(val deviceId: String) : PairEvent()
    data object ConfirmRemoveDevice : PairEvent()
    data object DismissRemoveDialog : PairEvent()
    data class QuickReconnect(val deviceId: String) : PairEvent()
    data class QuickNavigateCamera(val deviceId: String) : PairEvent()
    data class QuickNavigateMedia(val deviceId: String) : PairEvent()
}

class PairViewModel(
    private val parseQrPayload: ParseQrPayloadUseCase,
    private val pairStateMachine: PairStateMachine,
    private val connectionStateManager: ConnectionStateManager,
    private val deviceStorage: DeviceStorageRepository,
    private val wifiDirectConnector: WifiDirectConnector,
    private val sessionManager: SessionManager
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e("PairViewModel") { "Coroutine error: ${throwable.message}" }
        _state.value = _state.value.copy(
            isPairing = false,
            isWifiDirectPairing = false,
            wifiDirectStep = WifiDirectStep.Idle,
            lastError = throwable.message ?: "An unexpected error occurred"
        )
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private val _state = MutableStateFlow(PairUiState())
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<PairSideEffect>(extraBufferCapacity = 16)
    val sideEffects: SharedFlow<PairSideEffect> = _sideEffects.asSharedFlow()

    private var pendingPayload: QrPayload? = null
    private var trustContinuation: ((Boolean) -> Unit)? = null
    private var pendingWifiSsid: String? = null
    private var pendingWifiPassword: String? = null
    private var pendingWifiModelName: String? = null

    fun destroy() {
        scope.cancel()
    }

    init {
        loadPairedDevices()
        observePairProgress()

        // Auto-disconnect when camera Wi-Fi drops (camera turned off).
        // Update ConnectionStateManager immediately so the UI reflects the
        // disconnection without waiting for the SessionManager mutex.
        wifiDirectConnector.onNetworkLost = {
            Logger.i("PairViewModel") { "Camera Wi-Fi network lost — disconnecting" }
            connectionStateManager.disconnect()
            _state.value = _state.value.copy(
                isWifiDirectPairing = false,
                wifiDirectStep = WifiDirectStep.Idle
            )
            scope.launch {
                sessionManager.disconnect()
                _sideEffects.emit(PairSideEffect.ShowSnackbar("Camera disconnected"))
            }
        }
    }

    fun onEvent(event: PairEvent) {
        when (event) {
            PairEvent.ScanQrCode -> {
                scope.launch { _sideEffects.emit(PairSideEffect.RequestCameraPermission) }
            }
            is PairEvent.QrCodeScanned -> handleQrScanned(event.rawPayload)
            PairEvent.DismissScanner -> {
                _state.value = _state.value.copy(isScanning = false)
            }
            is PairEvent.ManualEndpointChanged -> {
                _state.value = _state.value.copy(manualEndpoint = event.value)
            }
            is PairEvent.ManualTokenChanged -> {
                _state.value = _state.value.copy(manualBindToken = event.value)
            }
            is PairEvent.ManualSsidChanged -> {
                _state.value = _state.value.copy(manualSsid = event.value)
            }
            is PairEvent.ManualWifiPasswordChanged -> {
                _state.value = _state.value.copy(manualWifiPassword = event.value)
            }
            PairEvent.ToggleTokenVisibility -> {
                _state.value = _state.value.copy(
                    isTokenVisible = !_state.value.isTokenVisible
                )
            }
            PairEvent.TogglePasswordVisibility -> {
                _state.value = _state.value.copy(
                    isPasswordVisible = !_state.value.isPasswordVisible
                )
            }
            PairEvent.ConnectManual -> connectManualStudioBox()
            PairEvent.ConnectWifiDirect -> connectManualWifiDirect()
            PairEvent.ToggleAdvancedManual -> {
                _state.value = _state.value.copy(
                    showAdvancedManual = !_state.value.showAdvancedManual
                )
            }
            is PairEvent.TrustConfirmed -> {
                trustContinuation?.invoke(event.trusted)
                trustContinuation = null
                _state.value = _state.value.copy(showTrustDialog = false)
            }
            PairEvent.CancelPairing -> {
                pairStateMachine.cancel()
                scope.launch { wifiDirectConnector.disconnect() }
                _state.value = _state.value.copy(
                    isPairing = false,
                    isWifiDirectPairing = false,
                    wifiDirectStep = WifiDirectStep.Idle
                )
            }
            PairEvent.RetryPairing -> retryPairing()
            PairEvent.SelectLiveControl -> {
                _state.value = _state.value.copy(showModePicker = false)
                scope.launch { _sideEffects.emit(PairSideEffect.NavigateToCamera) }
            }
            PairEvent.SelectMediaBrowser -> {
                _state.value = _state.value.copy(showModePicker = false)
                scope.launch { _sideEffects.emit(PairSideEffect.NavigateToMedia) }
            }
            PairEvent.DismissModePicker -> {
                _state.value = _state.value.copy(showModePicker = false)
            }
            PairEvent.Disconnect -> disconnect()
            PairEvent.Reconnect -> reconnect()
            PairEvent.OpenWifiSettings -> {
                scope.launch { _sideEffects.emit(PairSideEffect.OpenWifiSettings) }
            }
            PairEvent.OpenAppSettings -> {
                scope.launch { _sideEffects.emit(PairSideEffect.OpenAppSettings) }
            }
            PairEvent.DismissError -> {
                _state.value = _state.value.copy(lastError = null)
            }
            PairEvent.ScanNfc -> {
                _state.value = _state.value.copy(isNfcScanning = true)
                scope.launch { _sideEffects.emit(PairSideEffect.EnableNfcForegroundDispatch) }
            }
            is PairEvent.NfcTagRead -> {
                _state.value = _state.value.copy(isNfcScanning = false)
                scope.launch { _sideEffects.emit(PairSideEffect.DisableNfcForegroundDispatch) }
                handleQrScanned(event.rawPayload)
            }
            PairEvent.DismissNfcScanner -> {
                _state.value = _state.value.copy(isNfcScanning = false)
                scope.launch { _sideEffects.emit(PairSideEffect.DisableNfcForegroundDispatch) }
            }
            is PairEvent.RenameDevice -> renameDevice(event.deviceId, event.newName)
            is PairEvent.RequestRemoveDevice -> {
                _state.value = _state.value.copy(showRemoveDeviceDialog = event.deviceId)
            }
            PairEvent.ConfirmRemoveDevice -> confirmRemoveDevice()
            PairEvent.DismissRemoveDialog -> {
                _state.value = _state.value.copy(showRemoveDeviceDialog = null)
            }
            is PairEvent.QuickReconnect -> quickReconnect(event.deviceId)
            is PairEvent.QuickNavigateCamera -> quickNavigate(event.deviceId, PairSideEffect.NavigateToCamera)
            is PairEvent.QuickNavigateMedia -> quickNavigate(event.deviceId, PairSideEffect.NavigateToMedia)
        }
    }

    fun onCameraPermissionGranted() {
        _state.value = _state.value.copy(isScanning = true)
    }

    fun onNearbyWifiPermissionResult(granted: Boolean) {
        if (granted) {
            val ssid = pendingWifiSsid
            val password = pendingWifiPassword
            val modelName = pendingWifiModelName
            pendingWifiSsid = null
            pendingWifiPassword = null
            pendingWifiModelName = null
            if (ssid != null) {
                startWifiDirectPairing(ssid, password ?: "", modelName)
            }
        } else {
            pendingWifiSsid = null
            pendingWifiPassword = null
            pendingWifiModelName = null
            _state.value = _state.value.copy(
                isWifiDirectPairing = false,
                wifiDirectStep = WifiDirectStep.Failed,
                lastError = "Nearby Wi-Fi devices permission is required to connect to your camera. " +
                    "Please grant it when prompted."
            )
        }
    }

    fun updateNetworkInfo(ssid: String?, unavailableReason: String?, wifiConnected: Boolean) {
        _state.value = _state.value.copy(
            ssidName = ssid,
            ssidUnavailableReason = unavailableReason,
            wifiConnected = wifiConnected
        )
    }

    private fun handleQrScanned(rawPayload: String) {
        _state.value = _state.value.copy(isScanning = false)
        when (val result = parseQrPayload(rawPayload)) {
            is ParseResult.Success -> {
                pendingPayload = result.payload
                startPairing(result.payload)
            }
            is ParseResult.Expired -> {
                _state.value = _state.value.copy(
                    lastError = "QR code has expired. Please generate a new one."
                )
            }
            is ParseResult.UnsupportedVersion -> {
                _state.value = _state.value.copy(
                    lastError = "Unsupported QR code version (${result.version}). Please update the app."
                )
            }
            is ParseResult.Invalid -> {
                _state.value = _state.value.copy(
                    lastError = "Invalid QR code: ${result.reason}"
                )
            }
            is ParseResult.SonyDevice -> {
                val ssid = "DIRECT-${result.ssidSuffix}:${result.modelName}"
                startWifiDirectPairing(ssid, result.password, result.modelName)
            }
            is ParseResult.WifiCredentials -> {
                startWifiDirectPairing(result.ssid, result.password, null)
            }
            is ParseResult.UnrecognizedFormat -> {
                _state.value = _state.value.copy(
                    lastError = "This QR code is not a recognized camera Wi-Fi code. " +
                        "Most cameras (Canon, Nikon, Fujifilm, OM System) display the Wi-Fi name and password on screen — " +
                        "enter them manually below."
                )
            }
        }
    }

    private fun startWifiDirectPairing(ssid: String, password: String, modelName: String?) {
        _state.value = _state.value.copy(
            isWifiDirectPairing = true,
            wifiDirectStep = WifiDirectStep.Connecting,
            lastError = null
        )

        scope.launch {
            val result = wifiDirectConnector.connect(ssid, password)

            when (result) {
                is WifiDirectResult.Connected -> {
                    _state.value = _state.value.copy(wifiDirectStep = WifiDirectStep.DetectingCamera)

                    val brand = detectCameraBrand(ssid)
                    val deviceName = if (modelName != null) {
                        formatCameraDisplayName(brand, modelName)
                    } else {
                        brandDisplayName(brand, ssid)
                    }

                    _state.value = _state.value.copy(wifiDirectStep = WifiDirectStep.Saving)

                    val device = PairedDevice(
                        deviceId = "wd-${ssid.replace(Regex("[^A-Za-z0-9_-]"), "_")}",
                        deviceName = deviceName,
                        endpoint = "http://${result.gatewayIp}",
                        fingerprint = "",
                        connectionType = ConnectionType.WifiDirect,
                        cameraBrand = brand,
                        wifiSsid = ssid,
                        wifiPassword = password.ifBlank { null },
                        lastConnectedAt = currentEpochSeconds()
                    )

                    deviceStorage.savePairedDevice(device)

                    // Discover API endpoint and initialize camera session
                    _state.value = _state.value.copy(wifiDirectStep = WifiDirectStep.Initializing)
                    sessionManager.connect(device)
                    loadPairedDevices()

                    if (sessionManager.isConnected()) {
                        _state.value = _state.value.copy(
                            isWifiDirectPairing = false,
                            wifiDirectStep = WifiDirectStep.Done,
                            showModePicker = true
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isWifiDirectPairing = false,
                            wifiDirectStep = WifiDirectStep.Failed,
                            lastError = "Connected to camera Wi-Fi but could not reach camera API. " +
                                "Make sure your camera is in remote control mode."
                        )
                    }
                }

                is WifiDirectResult.UserCancelled -> {
                    _state.value = _state.value.copy(
                        isWifiDirectPairing = false,
                        wifiDirectStep = WifiDirectStep.Idle,
                        lastError = "Connection cancelled. You can try again or connect manually via Wi-Fi settings."
                    )
                }

                is WifiDirectResult.Failed -> {
                    _state.value = _state.value.copy(
                        isWifiDirectPairing = false,
                        wifiDirectStep = WifiDirectStep.Failed,
                        lastError = "Wi-Fi connection failed: ${result.reason}"
                    )
                }

                is WifiDirectResult.OpenWifiSettings -> {
                    _state.value = _state.value.copy(
                        isWifiDirectPairing = false,
                        wifiDirectStep = WifiDirectStep.Idle,
                        lastError = "Your device doesn't support automatic Wi-Fi connection. " +
                            "Please connect to \"$ssid\" manually in Wi-Fi settings, then return here."
                    )
                    _sideEffects.emit(PairSideEffect.OpenWifiSettings)
                }

                is WifiDirectResult.NeedsNearbyWifiPermission -> {
                    // Store pending connection for retry after permission is granted
                    pendingWifiSsid = ssid
                    pendingWifiPassword = password
                    pendingWifiModelName = modelName
                    _state.value = _state.value.copy(
                        isWifiDirectPairing = false,
                        wifiDirectStep = WifiDirectStep.Idle
                    )
                    _sideEffects.emit(PairSideEffect.RequestNearbyWifiPermission)
                }
            }
        }
    }

    private fun brandDisplayName(brand: CameraBrand, ssid: String): String = when (brand) {
        CameraBrand.Sony -> "Sony Camera"
        CameraBrand.Canon -> "Canon Camera"
        CameraBrand.Nikon -> "Nikon Camera"
        CameraBrand.Fujifilm -> "Fujifilm Camera"
        CameraBrand.Panasonic -> "Panasonic/Lumix Camera"
        CameraBrand.OmSystem -> "OM System Camera"
        CameraBrand.Unknown -> "Camera ($ssid)"
    }

    private fun connectManualWifiDirect() {
        val ssid = _state.value.manualSsid.trim()
        val password = _state.value.manualWifiPassword.trim()

        if (ssid.isBlank()) {
            _state.value = _state.value.copy(lastError = "Please enter the Wi-Fi network name (SSID)")
            return
        }

        startWifiDirectPairing(ssid, password, null)
    }

    private fun connectManualStudioBox() {
        val endpoint = _state.value.manualEndpoint.trim()
        val token = _state.value.manualBindToken.trim()

        if (endpoint.isBlank()) {
            _state.value = _state.value.copy(lastError = "Please enter the device endpoint")
            return
        }
        if (token.isBlank()) {
            _state.value = _state.value.copy(lastError = "Please enter the bind token")
            return
        }

        val payload = QrPayload(
            v = 1,
            deviceId = "manual-${endpoint.replace(Regex("[^A-Za-z0-9_.-]"), "_")}",
            deviceName = "Camera",
            endpoint = endpoint,
            fingerprint = "",
            bindToken = token,
            expiresAt = Long.MAX_VALUE
        )
        pendingPayload = payload
        startPairing(payload)
    }

    private fun startPairing(payload: QrPayload) {
        _state.value = _state.value.copy(isPairing = true, lastError = null)
        connectionStateManager.updateState(ConnectionState.Connecting)

        scope.launch {
            val result = pairStateMachine.startPairing(
                endpoint = payload.endpoint,
                bindToken = payload.bindToken,
                fingerprint = payload.fingerprint,
                deviceId = payload.deviceId,
                deviceName = payload.deviceName,
                onTrustConfirmation = { fingerprint ->
                    _state.value = _state.value.copy(
                        showTrustDialog = true,
                        trustFingerprint = fingerprint
                    )
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        trustContinuation = { trusted ->
                            @Suppress("DEPRECATION")
                            cont.resume(trusted, onCancellation = null)
                        }
                    }
                }
            )

            result.onSuccess { device ->
                connectionStateManager.setConnectedDevice(device)
                connectionStateManager.updateState(ConnectionState.Connected)
                deviceStorage.savePairedDevice(device)
                _state.value = _state.value.copy(
                    isPairing = false,
                    showModePicker = true
                )
                loadPairedDevices()
            }.onFailure { error ->
                connectionStateManager.updateState(ConnectionState.Failed)
                _state.value = _state.value.copy(
                    isPairing = false,
                    lastError = error.message ?: "Pairing failed"
                )
            }
        }
    }

    private fun retryPairing() {
        pendingPayload?.let { startPairing(it) }
    }

    private fun disconnect() {
        scope.launch {
            wifiDirectConnector.disconnect()
            sessionManager.disconnect()
        }
    }

    private fun reconnect() {
        scope.launch { sessionManager.reconnect() }
    }

    private fun renameDevice(deviceId: String, newName: String) {
        scope.launch {
            val device = deviceStorage.getPairedDevice(deviceId) ?: return@launch
            val updated = device.copy(customName = newName.trim().ifBlank { null })
            deviceStorage.savePairedDevice(updated)
            loadPairedDevices()
        }
    }

    private fun confirmRemoveDevice() {
        val deviceId = _state.value.showRemoveDeviceDialog ?: return
        _state.value = _state.value.copy(showRemoveDeviceDialog = null)
        scope.launch {
            // If removing the currently connected device, disconnect first
            val connectedDevice = connectionStateManager.connectedDevice.value
            if (connectedDevice?.deviceId == deviceId) {
                wifiDirectConnector.disconnect()
                sessionManager.disconnect()
            }
            deviceStorage.removePairedDevice(deviceId)
            loadPairedDevices()
        }
    }

    private fun quickReconnect(deviceId: String) {
        if (_state.value.isWifiDirectPairing) {
            scope.launch { _sideEffects.emit(PairSideEffect.ShowSnackbar("Connection already in progress")) }
            return
        }
        scope.launch {
            val device = deviceStorage.getPairedDevice(deviceId) ?: return@launch
            val ssid = device.wifiSsid
            if (ssid != null) {
                startWifiDirectPairing(ssid, device.wifiPassword ?: "", device.deviceName)
            } else {
                _sideEffects.emit(PairSideEffect.ShowSnackbar("Cannot reconnect — no saved Wi-Fi network"))
            }
        }
    }

    private fun quickNavigate(deviceId: String, destination: PairSideEffect) {
        if (_state.value.isWifiDirectPairing) {
            scope.launch { _sideEffects.emit(PairSideEffect.ShowSnackbar("Connection already in progress")) }
            return
        }
        scope.launch {
            val connectedDevice = connectionStateManager.connectedDevice.value
            if (connectedDevice?.deviceId == deviceId && sessionManager.isConnected()) {
                _sideEffects.emit(destination)
            } else {
                val device = deviceStorage.getPairedDevice(deviceId) ?: return@launch
                val ssid = device.wifiSsid
                if (ssid != null) {
                    // Reconnect first, then navigate on success
                    _state.value = _state.value.copy(
                        isWifiDirectPairing = true,
                        wifiDirectStep = WifiDirectStep.Connecting,
                        lastError = null
                    )
                    val result = wifiDirectConnector.connect(ssid, device.wifiPassword ?: "")
                    when (result) {
                        is WifiDirectResult.Connected -> {
                            _state.value = _state.value.copy(wifiDirectStep = WifiDirectStep.DetectingCamera)
                            val updatedDevice = device.copy(
                                endpoint = "http://${result.gatewayIp}",
                                lastConnectedAt = currentEpochSeconds()
                            )
                            deviceStorage.savePairedDevice(updatedDevice)
                            sessionManager.connect(updatedDevice)
                            loadPairedDevices()
                            if (sessionManager.isConnected()) {
                                _state.value = _state.value.copy(
                                    isWifiDirectPairing = false,
                                    wifiDirectStep = WifiDirectStep.Done
                                )
                                _sideEffects.emit(destination)
                            } else {
                                _state.value = _state.value.copy(
                                    isWifiDirectPairing = false,
                                    wifiDirectStep = WifiDirectStep.Failed,
                                    lastError = "Connected to Wi-Fi but camera API unreachable"
                                )
                            }
                        }
                        is WifiDirectResult.NeedsNearbyWifiPermission -> {
                            pendingWifiSsid = ssid
                            pendingWifiPassword = device.wifiPassword ?: ""
                            pendingWifiModelName = device.deviceName
                            _state.value = _state.value.copy(
                                isWifiDirectPairing = false,
                                wifiDirectStep = WifiDirectStep.Idle
                            )
                            _sideEffects.emit(PairSideEffect.RequestNearbyWifiPermission)
                        }
                        is WifiDirectResult.UserCancelled -> {
                            _state.value = _state.value.copy(
                                isWifiDirectPairing = false,
                                wifiDirectStep = WifiDirectStep.Idle
                            )
                        }
                        is WifiDirectResult.Failed -> {
                            _state.value = _state.value.copy(
                                isWifiDirectPairing = false,
                                wifiDirectStep = WifiDirectStep.Failed,
                                lastError = "Could not reconnect: ${result.reason}"
                            )
                        }
                        is WifiDirectResult.OpenWifiSettings -> {
                            _state.value = _state.value.copy(
                                isWifiDirectPairing = false,
                                wifiDirectStep = WifiDirectStep.Idle
                            )
                            _sideEffects.emit(PairSideEffect.OpenWifiSettings)
                        }
                    }
                } else {
                    _sideEffects.emit(PairSideEffect.ShowSnackbar("Cannot reconnect — no saved Wi-Fi network"))
                }
            }
        }
    }

    private fun loadPairedDevices() {
        scope.launch {
            val devices = deviceStorage.getPairedDevices()
            _state.value = _state.value.copy(pairedDevices = devices)
        }
    }

    private fun observePairProgress() {
        scope.launch {
            pairStateMachine.progress.collect { progress ->
                _state.value = _state.value.copy(pairProgress = progress)
            }
        }
    }
}
