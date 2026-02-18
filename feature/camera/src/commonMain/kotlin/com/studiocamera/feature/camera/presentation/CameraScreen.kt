package com.studiocamera.feature.camera.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.studiocamera.core.common.MockModeManager
import com.studiocamera.core.designsystem.component.ConnectionGate
import com.studiocamera.core.designsystem.component.rememberSessionErrorState
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.FlashMode
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.feature.camera.presentation.component.CameraTopBar
import com.studiocamera.feature.camera.presentation.component.CaptureButton
import com.studiocamera.feature.camera.presentation.component.ControlStrip
import com.studiocamera.feature.camera.presentation.component.GalleryThumbnail
import com.studiocamera.feature.camera.presentation.component.LiveViewSurface
import com.studiocamera.feature.camera.presentation.component.MockLiveViewPlaceholder
import com.studiocamera.feature.camera.presentation.component.ModeSwitcher
import com.studiocamera.feature.camera.presentation.component.SideControlsLeft
import com.studiocamera.feature.camera.presentation.component.SideControlsRight
import com.studiocamera.feature.camera.presentation.component.TimerCountdown
import com.studiocamera.feature.camera.presentation.component.ValueSelector
import com.studiocamera.feature.camera.presentation.component.ZoomControl
import com.studiocamera.feature.camera.presentation.overlay.FocusPeakingOverlay
import com.studiocamera.feature.camera.presentation.overlay.GridOverlay
import com.studiocamera.feature.camera.presentation.overlay.GridType
import com.studiocamera.feature.camera.presentation.overlay.HistogramOverlay
import com.studiocamera.feature.camera.presentation.overlay.OverlayConfig
import com.studiocamera.feature.camera.presentation.overlay.SafeZone916Overlay
import com.studiocamera.feature.camera.presentation.overlay.SafeZoneOverlay
import com.studiocamera.feature.camera.presentation.overlay.ZebraOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    connectionState: ConnectionState,
    onNavigateToHome: () -> Unit,
    onNavigateToMedia: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sessionManager: SessionManager = koinInject()
    val errorState by rememberSessionErrorState(sessionManager, connectionState)
    val retryScope = rememberCoroutineScope()

    ConnectionGate(
        connectionState = connectionState,
        featureName = "Camera",
        onNavigateToHome = onNavigateToHome,
        modifier = modifier,
        lastError = errorState.lastError,
        reconnectAttempt = errorState.reconnectAttempt,
        onRetry = { retryScope.launch { sessionManager.reconnect() } },
        onDismissError = {}
    ) {
        CameraContent(connectionState = connectionState, onNavigateToMedia = onNavigateToMedia)
    }
}

@Composable
private fun CameraContent(connectionState: ConnectionState, onNavigateToMedia: () -> Unit) {
    val cameraRepository: CameraRepository = koinInject()
    val sessionManager: SessionManager = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val mockModeManager: MockModeManager = koinInject()
    val connectionStateManager: ConnectionStateManager = koinInject()
    val isMockMode by mockModeManager.isMockActive.collectAsState()
    val cameraState by cameraRepository.cameraState.collectAsState()
    val appSettings by settingsRepository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val capabilities = sessionManager.currentCapabilities() ?: DeviceCapabilities()

    // Screen state
    var screenState by remember(appSettings.defaultGrid, appSettings.defaultSafeZone) {
        mutableStateOf(
            CameraScreenState(
                overlayConfig = OverlayConfig(
                    gridType = if (appSettings.defaultGrid) GridType.RuleOfThirds else GridType.None,
                    showSafeZone = appSettings.defaultSafeZone
                )
            )
        )
    }

    // Sync initial shoot mode to the UI state
    LaunchedEffect(cameraState.valuesReported) {
        if (cameraState.valuesReported) {
            val actualMode = if (cameraState.shootMode == "movie") CaptureMode.Video else CaptureMode.Photo
            if (screenState.captureMode != actualMode) {
                screenState = screenState.copy(captureMode = actualMode)
            }
        }
    }

    // Recording timer
    val recordingSeconds = remember { mutableIntStateOf(0) }
    LaunchedEffect(cameraState.isRecording) {
        if (cameraState.isRecording) {
            recordingSeconds.intValue = 0
            while (isActive) {
                delay(1000)
                recordingSeconds.intValue++
            }
        } else {
            recordingSeconds.intValue = 0
        }
    }

    // Timer countdown
    LaunchedEffect(screenState.timerCountdown) {
        val countdown = screenState.timerCountdown
        if (countdown != null && countdown > 0) {
            delay(1000)
            screenState = screenState.copy(timerCountdown = countdown - 1)
        } else if (countdown == 0) {
            // Timer reached zero — fire capture
            screenState = screenState.copy(timerCountdown = null)
            when (screenState.captureMode) {
                CaptureMode.Photo -> {
                    if (cameraState.shootMode == "movie") {
                        snackbarHostState.showSnackbar("Cannot take photo in video mode")
                    } else {
                        val result = cameraRepository.capturePhoto()
                        when (result) {
                            is ApiResult.Success -> snackbarHostState.showSnackbar("Photo captured")
                            is ApiResult.Error -> snackbarHostState.showSnackbar("Capture failed: ${result.error.message}")
                        }
                    }
                }
                CaptureMode.Video -> {
                    if (!cameraState.isRecording) {
                        cameraRepository.startRecording()
                    }
                }
            }
        }
    }

    // Focus indicator
    var focusTapPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    LaunchedEffect(focusTapPosition) {
        if (focusTapPosition != null) {
            delay(1500)
            focusTapPosition = null
        }
    }

    val frameFlow = remember(cameraRepository) { cameraRepository.liveViewFrames() }

    // Animated zoom
    val animatedZoom by animateFloatAsState(
        targetValue = screenState.zoomLevel,
        animationSpec = tween(200)
    )

    // Capture handler
    val onCapture: () -> Unit = {
        if (screenState.timerSeconds > 0 && screenState.timerCountdown == null) {
            // Start countdown
            screenState = screenState.copy(timerCountdown = screenState.timerSeconds)
        } else {
            scope.launch {
                when (screenState.captureMode) {
                    CaptureMode.Photo -> {
                        if (cameraState.shootMode == "movie") {
                            snackbarHostState.showSnackbar("Cannot take photo in video mode")
                        } else {
                            val result = cameraRepository.capturePhoto()
                            when (result) {
                                is ApiResult.Success -> snackbarHostState.showSnackbar("Photo captured")
                                is ApiResult.Error -> snackbarHostState.showSnackbar("Capture failed: ${result.error.message}")
                            }
                        }
                    }
                    CaptureMode.Video -> {
                        if (cameraState.isRecording) {
                            val result = cameraRepository.stopRecording()
                            when (result) {
                                is ApiResult.Success -> snackbarHostState.showSnackbar("Recording saved")
                                is ApiResult.Error -> snackbarHostState.showSnackbar("Failed: ${result.error.message}")
                            }
                        } else {
                            val result = cameraRepository.startRecording()
                            if (result is ApiResult.Error) {
                                snackbarHostState.showSnackbar("Failed: ${result.error.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    val overlayConfig = screenState.overlayConfig

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            LandscapeCamera(
                isMockMode = isMockMode,
                frameFlow = frameFlow,
                cameraState = cameraState,
                screenState = screenState,
                connectionState = connectionState,
                overlayConfig = overlayConfig,
                animatedZoom = animatedZoom,
                capabilities = capabilities,
                recordingSeconds = recordingSeconds.intValue,
                focusTapPosition = focusTapPosition,
                snackbarHostState = snackbarHostState,
                onCapture = onCapture,
                onNavigateToMedia = onNavigateToMedia,
                onZoomChange = { screenState = screenState.copy(zoomLevel = it) },
                onScreenStateChange = { screenState = it },
                onSelectorClick = { screenState = screenState.copy(activeSelector = it) },
                onFocusTap = { x, y, rawX, rawY ->
                    focusTapPosition = rawX to rawY
                    scope.launch { cameraRepository.tapToFocus(x, y) }
                },
                onShootModeChange = { mode -> scope.launch { cameraRepository.setShootMode(mode) } },
                onUpdateSetting = { iso, ss, av, ev, flash, fmt, vRes, vFps ->
                    scope.launch {
                        cameraRepository.updateSettings(
                            iso = iso, shutterSpeed = ss, aperture = av, ev = ev,
                            flashMode = flash, imageFormat = fmt,
                            videoResolution = vRes, videoFps = vFps
                        )
                    }
                }
            )
        } else {
            PortraitCamera(
                isMockMode = isMockMode,
                frameFlow = frameFlow,
                cameraState = cameraState,
                screenState = screenState,
                connectionState = connectionState,
                overlayConfig = overlayConfig,
                animatedZoom = animatedZoom,
                capabilities = capabilities,
                recordingSeconds = recordingSeconds.intValue,
                focusTapPosition = focusTapPosition,
                snackbarHostState = snackbarHostState,
                onCapture = onCapture,
                onNavigateToMedia = onNavigateToMedia,
                onZoomChange = { screenState = screenState.copy(zoomLevel = it) },
                onScreenStateChange = { screenState = it },
                onSelectorClick = { screenState = screenState.copy(activeSelector = it) },
                onFocusTap = { x, y, rawX, rawY ->
                    focusTapPosition = rawX to rawY
                    scope.launch { cameraRepository.tapToFocus(x, y) }
                },
                onShootModeChange = { mode -> scope.launch { cameraRepository.setShootMode(mode) } },
                onUpdateSetting = { iso, ss, av, ev, flash, fmt, vRes, vFps ->
                    scope.launch {
                        cameraRepository.updateSettings(
                            iso = iso, shutterSpeed = ss, aperture = av, ev = ev,
                            flashMode = flash, imageFormat = fmt,
                            videoResolution = vRes, videoFps = vFps
                        )
                    }
                }
            )
        }
    }

    // Value selector bottom sheet
    screenState.activeSelector?.let { selector ->
        ValueSelector(
            selectorType = selector,
            cameraState = cameraState,
            screenState = screenState,
            onDismiss = { screenState = screenState.copy(activeSelector = null) },
            onUpdateSetting = { iso, ss, av, ev, flash, fmt, vRes, vFps ->
                scope.launch {
                    cameraRepository.updateSettings(
                        iso = iso, shutterSpeed = ss, aperture = av, ev = ev,
                        flashMode = flash, imageFormat = fmt,
                        videoResolution = vRes, videoFps = vFps
                    )
                }
            },
            onUpdateScreenState = { screenState = it },
            onUpdateWhiteBalance = { wb ->
                scope.launch { cameraRepository.updateSettings(whiteBalance = wb) }
            },
            onUpdateExposureMode = { em ->
                scope.launch { cameraRepository.updateSettings(exposureMode = em) }
            },
            onUpdatePhotoResolution = { pr ->
                scope.launch { cameraRepository.updateSettings(photoResolution = pr) }
            }
        )
    }
}

@Composable
private fun PortraitCamera(
    isMockMode: Boolean,
    frameFlow: kotlinx.coroutines.flow.Flow<ByteArray>,
    cameraState: com.studiocamera.core.domain.model.CameraState,
    screenState: CameraScreenState,
    connectionState: ConnectionState,
    overlayConfig: OverlayConfig,
    animatedZoom: Float,
    capabilities: DeviceCapabilities,
    recordingSeconds: Int,
    focusTapPosition: Pair<Float, Float>?,
    snackbarHostState: SnackbarHostState,
    onCapture: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onScreenStateChange: (CameraScreenState) -> Unit,
    onSelectorClick: (SelectorType) -> Unit,
    onFocusTap: (Float, Float, Float, Float) -> Unit,
    onShootModeChange: (String) -> Unit,
    onUpdateSetting: (Int?, String?, Float?, Float?, FlashMode?, com.studiocamera.core.domain.model.ImageFormat?, String?, Int?) -> Unit
) {
    var streamAspectRatio by remember { mutableStateOf(4f / 3f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen live view with transforms
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(capabilities.supportsManualFocus) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (capabilities.supportsManualFocus) {
                                val normalizedX = offset.x / size.width
                                val normalizedY = offset.y / size.height
                                onFocusTap(normalizedX, normalizedY, offset.x, offset.y)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Live view wrapper constrained by aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(streamAspectRatio, matchHeightConstraintsFirst = false)
            ) {
                // Live view with zoom, flip, invert
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (screenState.isFlipped) -animatedZoom else animatedZoom
                            scaleY = animatedZoom
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isMockMode) {
                        MockLiveViewPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        LiveViewSurface(
                            frameFlow = frameFlow,
                            modifier = Modifier.fillMaxSize(),
                            onAspectRatioChange = { ratio ->
                                if (ratio > 0 && streamAspectRatio != ratio) {
                                    streamAspectRatio = ratio
                                }
                            }
                        )
                    }
                }

                // Overlays (not affected by zoom/flip, but constrained to aspect ratio bounds)
                LiveViewOverlays(overlayConfig = overlayConfig)
            }

            // Focus indicator
            FocusIndicator(focusTapPosition)

            // Timer countdown overlay
            screenState.timerCountdown?.let { countdown ->
                if (countdown > 0) {
                    TimerCountdown(secondsRemaining = countdown)
                }
            }
        }

        // Top bar
        CameraTopBar(
            cameraState = cameraState,
            connectionState = connectionState,
            recordingSeconds = recordingSeconds,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
        )

        // Side controls - left
        SideControlsLeft(
            cameraState = cameraState,
            captureMode = screenState.captureMode,
            onFormatClick = { onSelectorClick(SelectorType.Format) },
            onPhotoResolutionClick = { onSelectorClick(SelectorType.PhotoResolution) },
            onVideoResolutionClick = { onSelectorClick(SelectorType.VideoResolution) },
            onFpsClick = { onSelectorClick(SelectorType.VideoFps) },
            onHdrToggle = {
                onUpdateSetting(null, null, null, null, null, null, null, null)
                onScreenStateChange(screenState)
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        )

        // Side controls - right
        SideControlsRight(
            flashMode = cameraState.flashMode,
            timerSeconds = screenState.timerSeconds,
            onFlashClick = { onSelectorClick(SelectorType.Flash) },
            onTimerClick = { onSelectorClick(SelectorType.Timer) },
            onAspectRatioClick = { onSelectorClick(SelectorType.AspectRatio) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 4.dp)
        )

        // Histogram (if enabled)
        if (overlayConfig.showHistogram) {
            HistogramOverlay(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 220.dp)
                    .size(width = 100.dp, height = 60.dp)
            )
        }

        // Zoom control
        ZoomControl(
            zoomLevel = screenState.zoomLevel,
            onZoomChange = onZoomChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 200.dp)
        )

        // Bottom panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(bottom = 16.dp)
        ) {
            // Control strip
            ControlStrip(
                cameraState = cameraState,
                captureMode = screenState.captureMode,
                overlayConfig = overlayConfig,
                isFlipped = screenState.isFlipped,
                isInverted = screenState.isInverted,
                activeSelector = screenState.activeSelector,
                onItemClick = onSelectorClick,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Capture row: gallery | capture button | spacer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GalleryThumbnail(onClick = onNavigateToMedia)

                CaptureButton(
                    captureMode = screenState.captureMode,
                    isRecording = cameraState.isRecording,
                    onClick = onCapture
                )

                // Flip / invert toggle area
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Mode switcher
            ModeSwitcher(
                currentMode = screenState.captureMode,
                onModeChange = { newMode ->
                    onScreenStateChange(screenState.copy(captureMode = newMode))
                    onShootModeChange(if (newMode == CaptureMode.Video) "movie" else "still")
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun LandscapeCamera(
    isMockMode: Boolean,
    frameFlow: kotlinx.coroutines.flow.Flow<ByteArray>,
    cameraState: com.studiocamera.core.domain.model.CameraState,
    screenState: CameraScreenState,
    connectionState: ConnectionState,
    overlayConfig: OverlayConfig,
    animatedZoom: Float,
    capabilities: DeviceCapabilities,
    recordingSeconds: Int,
    focusTapPosition: Pair<Float, Float>?,
    snackbarHostState: SnackbarHostState,
    onCapture: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onScreenStateChange: (CameraScreenState) -> Unit,
    onSelectorClick: (SelectorType) -> Unit,
    onFocusTap: (Float, Float, Float, Float) -> Unit,
    onShootModeChange: (String) -> Unit,
    onUpdateSetting: (Int?, String?, Float?, Float?, FlashMode?, com.studiocamera.core.domain.model.ImageFormat?, String?, Int?) -> Unit
) {
    var streamAspectRatio by remember { mutableStateOf(4f / 3f) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(110.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            SideControlsLeft(
                cameraState = cameraState,
                captureMode = screenState.captureMode,
                onFormatClick = { onSelectorClick(SelectorType.Format) },
                onPhotoResolutionClick = { onSelectorClick(SelectorType.PhotoResolution) },
                onVideoResolutionClick = { onSelectorClick(SelectorType.VideoResolution) },
                onFpsClick = { onSelectorClick(SelectorType.VideoFps) },
                onHdrToggle = {
                    onUpdateSetting(null, null, null, null, null, null, null, null)
                    onScreenStateChange(screenState)
                }
            )
        }

        // Live view area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(capabilities.supportsManualFocus) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (capabilities.supportsManualFocus) {
                                val normalizedX = offset.x / size.width
                                val normalizedY = offset.y / size.height
                                onFocusTap(normalizedX, normalizedY, offset.x, offset.y)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Live view wrapper constrained by aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(streamAspectRatio, matchHeightConstraintsFirst = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (screenState.isFlipped) -animatedZoom else animatedZoom
                            scaleY = animatedZoom
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isMockMode) {
                        MockLiveViewPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        LiveViewSurface(
                            frameFlow = frameFlow,
                            modifier = Modifier.fillMaxSize(),
                            onAspectRatioChange = { ratio ->
                                if (ratio > 0 && streamAspectRatio != ratio) {
                                    streamAspectRatio = ratio
                                }
                            }
                        )
                    }
                }

                LiveViewOverlays(overlayConfig = overlayConfig)
            }

            FocusIndicator(focusTapPosition)

            screenState.timerCountdown?.let { countdown ->
                if (countdown > 0) {
                    TimerCountdown(secondsRemaining = countdown)
                }
            }

            // Top bar overlay
            CameraTopBar(
                cameraState = cameraState,
                connectionState = connectionState,
                recordingSeconds = recordingSeconds,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            )

            // Zoom control
            ZoomControl(
                zoomLevel = screenState.zoomLevel,
                onZoomChange = onZoomChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )

            if (overlayConfig.showHistogram) {
                HistogramOverlay(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(width = 100.dp, height = 60.dp)
                )
            }
        }

        // Right panel
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(110.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            SideControlsRight(
                flashMode = cameraState.flashMode,
                timerSeconds = screenState.timerSeconds,
                onFlashClick = { onSelectorClick(SelectorType.Flash) },
                onTimerClick = { onSelectorClick(SelectorType.Timer) },
                onAspectRatioClick = { onSelectorClick(SelectorType.AspectRatio) }
            )

            CaptureButton(
                captureMode = screenState.captureMode,
                isRecording = cameraState.isRecording,
                onClick = onCapture
            )

            ModeSwitcher(
                currentMode = screenState.captureMode,
                onModeChange = { newMode ->
                    onScreenStateChange(screenState.copy(captureMode = newMode))
                    onShootModeChange(if (newMode == CaptureMode.Video) "movie" else "still")
                }
            )
        }
    }

    // Snackbar
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
    )
}

@Composable
private fun LiveViewOverlays(overlayConfig: OverlayConfig) {
    if (overlayConfig.gridType != GridType.None) {
        GridOverlay(
            gridType = overlayConfig.gridType,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (overlayConfig.showSafeZone) {
        SafeZoneOverlay(modifier = Modifier.fillMaxSize())
    }

    if (overlayConfig.show916SafeZone) {
        SafeZone916Overlay(modifier = Modifier.fillMaxSize())
    }

    if (overlayConfig.showFocusPeaking) {
        FocusPeakingOverlay(
            focusMap = null,
            peakingColor = Color(overlayConfig.focusPeakingColor.toULong()),
            modifier = Modifier.fillMaxSize()
        )
    }

    if (overlayConfig.showZebra) {
        ZebraOverlay(
            overexposedMap = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FocusIndicator(focusTapPosition: Pair<Float, Float>?) {
    focusTapPosition?.let { (x, y) ->
        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt() - 24, y.roundToInt() - 24) }
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
            )
        }
    }
}
