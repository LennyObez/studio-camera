package com.studiocamera.feature.camera.presentation

import com.studiocamera.feature.camera.presentation.overlay.GridType
import com.studiocamera.feature.camera.presentation.overlay.OverlayConfig

enum class CaptureMode { Photo, Video }

enum class SelectorType {
    WhiteBalance, Format, Grid, ExposureMode,
    Aperture, ShutterSpeed, ISO, EV,
    Flash, Timer, AspectRatio, VideoResolution, VideoFps,
    PhotoResolution
}

data class CameraScreenState(
    val captureMode: CaptureMode = CaptureMode.Photo,
    val timerSeconds: Int = 0,
    val timerCountdown: Int? = null,
    val zoomLevel: Float = 1f,
    val isFlipped: Boolean = false,
    val isInverted: Boolean = false,
    val lastCapturedPhotoUrl: String? = null,
    val activeSelector: SelectorType? = null,
    val overlayConfig: OverlayConfig = OverlayConfig()
)
