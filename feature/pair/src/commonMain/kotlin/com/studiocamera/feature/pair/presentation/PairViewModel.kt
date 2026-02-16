package com.studiocamera.feature.pair.presentation

import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.ParseResult
import com.studiocamera.core.domain.model.QrPayload
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.usecase.ParseQrPayloadUseCase
import com.studiocamera.feature.pair.domain.PairProgress
import com.studiocamera.feature.pair.domain.PairStateMachine
import co.touchlab.kermit.Logger
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
    val isTokenVisible: Boolean = false,
    val isScanning: Boolean = false,
    val isPairing: Boolean = false,
    val pairProgress: PairProgress = PairProgress(),
    val showModePicker: Boolean = false,
    val showTrustDialog: Boolean = false,
    val trustFingerprint: String = "",
    val pairedDevices: List<PairedDevice> = emptyList(),
    val lastError: String? = null,
    val ssidName: String? = null,
    val ssidUnavailableReason: String? = null,
    val wifiConnected: Boolean = false
)

sealed class PairSideEffect {
    data class ShowSnackbar(val message: String) : PairSideEffect()
    data object NavigateToCamera : PairSideEffect()
    data object NavigateToMedia : PairSideEffect()
    data object OpenWifiSettings : PairSideEffect()
    data object OpenAppSettings : PairSideEffect()
    data object RequestCameraPermission : PairSideEffect()
    data object RequestLocationPermission : PairSideEffect()
}

sealed class PairEvent {
    data object ScanQrCode : PairEvent()
    data class QrCodeScanned(val rawPayload: String) : PairEvent()
    data object DismissScanner : PairEvent()
    data class ManualEndpointChanged(val value: String) : PairEvent()
    data class ManualTokenChanged(val value: String) : PairEvent()
    data object ToggleTokenVisibility : PairEvent()
    data object ConnectManual : PairEvent()
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
}

class PairViewModel(
    private val parseQrPayload: ParseQrPayloadUseCase,
    private val pairStateMachine: PairStateMachine,
    private val connectionStateManager: ConnectionStateManager,
    private val deviceStorage: DeviceStorageRepository
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e("PairViewModel") { "Coroutine error: ${throwable.message}" }
        _state.value = _state.value.copy(
            isPairing = false,
            lastError = throwable.message ?: "An unexpected error occurred"
        )
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    private val _state = MutableStateFlow(PairUiState())
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<PairSideEffect>()
    val sideEffects: SharedFlow<PairSideEffect> = _sideEffects.asSharedFlow()

    private var pendingPayload: QrPayload? = null
    private var trustContinuation: ((Boolean) -> Unit)? = null

    init {
        loadPairedDevices()
        observePairProgress()
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
            PairEvent.ToggleTokenVisibility -> {
                _state.value = _state.value.copy(
                    isTokenVisible = !_state.value.isTokenVisible
                )
            }
            PairEvent.ConnectManual -> connectManual()
            is PairEvent.TrustConfirmed -> {
                trustContinuation?.invoke(event.trusted)
                trustContinuation = null
                _state.value = _state.value.copy(showTrustDialog = false)
            }
            PairEvent.CancelPairing -> {
                pairStateMachine.cancel()
                _state.value = _state.value.copy(isPairing = false)
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
        }
    }

    fun onCameraPermissionGranted() {
        _state.value = _state.value.copy(isScanning = true)
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
        }
    }

    private fun connectManual() {
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
            deviceId = "manual-${endpoint.hashCode()}",
            deviceName = "Studio Camera",
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
        connectionStateManager.disconnect()
    }

    private fun reconnect() {
        // Will be handled by SessionManager in Phase 2
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
