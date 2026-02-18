package com.studiocamera.feature.pair.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.feature.pair.presentation.component.CameraCard
import com.studiocamera.feature.pair.presentation.component.ConnectStepper
import com.studiocamera.feature.pair.presentation.component.ManualEntryForm
import com.studiocamera.feature.pair.presentation.component.ModePicker
import com.studiocamera.feature.pair.presentation.component.NfcScanOverlay
import com.studiocamera.feature.pair.presentation.component.QrScanner
import com.studiocamera.feature.pair.presentation.component.RemoveDeviceDialog
import com.studiocamera.feature.pair.presentation.component.TipsAndGuidesSection
import com.studiocamera.feature.pair.presentation.component.TrustDialog
import org.koin.compose.koinInject

@Composable
fun PairScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onOpenWifiSettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRequestCameraPermission: (onGranted: () -> Unit) -> Unit = { it() },
    onRequestNearbyWifiPermission: (onResult: (Boolean) -> Unit) -> Unit = { it(true) },
    modifier: Modifier = Modifier,
    viewModel: PairViewModel = koinInject()
) {
    val state by viewModel.state.collectAsState()
    val connectionStateManager: ConnectionStateManager = koinInject()
    val connectionState by connectionStateManager.state.collectAsState()
    val connectedDevice by connectionStateManager.connectedDevice.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                PairSideEffect.NavigateToCamera -> onNavigateToCamera()
                PairSideEffect.NavigateToMedia -> onNavigateToMedia()
                is PairSideEffect.ShowSnackbar -> { /* handled by snackbar host */ }
                PairSideEffect.OpenWifiSettings -> onOpenWifiSettings()
                PairSideEffect.OpenAppSettings -> onOpenAppSettings()
                PairSideEffect.RequestCameraPermission -> {
                    onRequestCameraPermission { viewModel.onCameraPermissionGranted() }
                }
                PairSideEffect.RequestNearbyWifiPermission -> {
                    onRequestNearbyWifiPermission { granted ->
                        viewModel.onNearbyWifiPermissionResult(granted)
                    }
                }
                PairSideEffect.RequestLocationPermission -> { /* platform-specific */ }
                PairSideEffect.EnableNfcForegroundDispatch -> { /* handled by platform */ }
                PairSideEffect.DisableNfcForegroundDispatch -> { /* handled by platform */ }
            }
        }
    }

    // QR Scanner overlay
    if (state.isScanning) {
        QrScanner(
            onQrCodeScanned = { rawPayload ->
                viewModel.onEvent(PairEvent.QrCodeScanned(rawPayload))
            },
            onClose = {
                viewModel.onEvent(PairEvent.DismissScanner)
            }
        )
        return
    }

    // NFC Scan overlay
    if (state.isNfcScanning) {
        NfcScanOverlay(
            onClose = { viewModel.onEvent(PairEvent.DismissNfcScanner) }
        )
        return
    }

    // Trust confirmation dialog
    if (state.showTrustDialog) {
        TrustDialog(
            fingerprint = state.trustFingerprint,
            onAccept = { viewModel.onEvent(PairEvent.TrustConfirmed(true)) },
            onReject = { viewModel.onEvent(PairEvent.TrustConfirmed(false)) }
        )
    }

    // Mode picker bottom sheet
    if (state.showModePicker) {
        ModePicker(
            onSelectLiveControl = { viewModel.onEvent(PairEvent.SelectLiveControl) },
            onSelectMediaBrowser = { viewModel.onEvent(PairEvent.SelectMediaBrowser) },
            onDismiss = { viewModel.onEvent(PairEvent.DismissModePicker) }
        )
    }

    // Remove device confirmation dialog
    state.showRemoveDeviceDialog?.let { deviceId ->
        val device = state.pairedDevices.find { it.deviceId == deviceId }
        if (device != null) {
            RemoveDeviceDialog(
                deviceName = device.displayName,
                onConfirm = { viewModel.onEvent(PairEvent.ConfirmRemoveDevice) },
                onDismiss = { viewModel.onEvent(PairEvent.DismissRemoveDialog) }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === Section 1: Connected Camera Card ===
        connectedDevice?.let { device ->
            if (connectionState != ConnectionState.Disconnected) {
                CameraCard(
                    device = device,
                    isConnected = connectionState == ConnectionState.Connected,
                    connectionState = connectionState,
                    onReconnect = { viewModel.onEvent(PairEvent.Reconnect) },
                    onNavigateCamera = { viewModel.onEvent(PairEvent.QuickNavigateCamera(device.deviceId)) },
                    onNavigateMedia = { viewModel.onEvent(PairEvent.QuickNavigateMedia(device.deviceId)) },
                    onRename = { newName -> viewModel.onEvent(PairEvent.RenameDevice(device.deviceId, newName)) },
                    onRemove = { viewModel.onEvent(PairEvent.RequestRemoveDevice(device.deviceId)) }
                )
            }
        }

        // === Section 2: Saved Cameras ===
        val savedDevices = state.pairedDevices.filter { saved ->
            connectedDevice?.let { connected ->
                saved.deviceId != connected.deviceId || connectionState == ConnectionState.Disconnected
            } ?: true
        }

        if (savedDevices.isNotEmpty()) {
            Text(
                text = "Your cameras",
                style = MaterialTheme.typography.titleMedium
            )
            savedDevices.forEach { device ->
                CameraCard(
                    device = device,
                    isConnected = false,
                    connectionState = ConnectionState.Disconnected,
                    onReconnect = { viewModel.onEvent(PairEvent.QuickReconnect(device.deviceId)) },
                    onNavigateCamera = { viewModel.onEvent(PairEvent.QuickNavigateCamera(device.deviceId)) },
                    onNavigateMedia = { viewModel.onEvent(PairEvent.QuickNavigateMedia(device.deviceId)) },
                    onRename = { newName -> viewModel.onEvent(PairEvent.RenameDevice(device.deviceId, newName)) },
                    onRemove = { viewModel.onEvent(PairEvent.RequestRemoveDevice(device.deviceId)) }
                )
            }
        } else if (state.pairedDevices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No cameras saved yet",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Pair your first camera below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // === Section 3: Active pairing/connection overlays ===
        // Wi-Fi Direct pairing progress
        if (state.isWifiDirectPairing) {
            WifiDirectProgressCard(
                step = state.wifiDirectStep,
                onCancel = { viewModel.onEvent(PairEvent.CancelPairing) }
            )
        }

        // Pairing progress
        if (state.isPairing) {
            ConnectStepper(
                progress = state.pairProgress,
                onCancel = { viewModel.onEvent(PairEvent.CancelPairing) },
                onRetry = { viewModel.onEvent(PairEvent.RetryPairing) }
            )
        }

        // Error display
        if (state.lastError != null) {
            ErrorCard(
                message = state.lastError!!,
                onDismiss = { viewModel.onEvent(PairEvent.DismissError) },
                onRetry = { viewModel.onEvent(PairEvent.RetryPairing) }
            )
        }

        // === Section 4: Connect a New Camera ===
        if (!state.isPairing && !state.isWifiDirectPairing) {
            Text(
                text = "Connect a new camera",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { viewModel.onEvent(PairEvent.ScanQrCode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan camera QR code")
            }

            OutlinedButton(
                onClick = { viewModel.onEvent(PairEvent.ScanNfc) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tap NFC tag")
            }

            // Manual Entry (Wi-Fi Direct SSID + password)
            ManualEntryForm(
                ssid = state.manualSsid,
                wifiPassword = state.manualWifiPassword,
                isPasswordVisible = state.isPasswordVisible,
                onSsidChanged = { viewModel.onEvent(PairEvent.ManualSsidChanged(it)) },
                onWifiPasswordChanged = { viewModel.onEvent(PairEvent.ManualWifiPasswordChanged(it)) },
                onTogglePasswordVisibility = { viewModel.onEvent(PairEvent.TogglePasswordVisibility) },
                onConnectWifiDirect = { viewModel.onEvent(PairEvent.ConnectWifiDirect) },
                showAdvanced = state.showAdvancedManual,
                onToggleAdvanced = { viewModel.onEvent(PairEvent.ToggleAdvancedManual) },
                endpoint = state.manualEndpoint,
                bindToken = state.manualBindToken,
                isTokenVisible = state.isTokenVisible,
                onEndpointChanged = { viewModel.onEvent(PairEvent.ManualEndpointChanged(it)) },
                onTokenChanged = { viewModel.onEvent(PairEvent.ManualTokenChanged(it)) },
                onToggleTokenVisibility = { viewModel.onEvent(PairEvent.ToggleTokenVisibility) },
                onConnectStudioBox = { viewModel.onEvent(PairEvent.ConnectManual) }
            )
        }

        // === Section 5: Tips & Guides ===
        TipsAndGuidesSection(
            pairedBrands = state.pairedDevices.map { it.cameraBrand }.toSet(),
            onOpenWifiSettings = { viewModel.onEvent(PairEvent.OpenWifiSettings) }
        )

        // Bottom spacer for scroll padding
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WifiDirectProgressCard(
    step: WifiDirectStep,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connecting to Camera Wi-Fi",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = when (step) {
                        WifiDirectStep.Idle -> "Preparing..."
                        WifiDirectStep.Connecting -> "Connecting to camera Wi-Fi network..."
                        WifiDirectStep.DetectingCamera -> "Detecting camera brand..."
                        WifiDirectStep.Saving -> "Saving camera..."
                        WifiDirectStep.Initializing -> "Initializing camera session..."
                        WifiDirectStep.Done -> "Connected!"
                        WifiDirectStep.Failed -> "Connection failed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
