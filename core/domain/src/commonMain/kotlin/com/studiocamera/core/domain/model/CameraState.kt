package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraState(
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val iso: Int = 100,
    val shutterSpeed: String = "1/60",
    val aperture: Float = 2.8f,
    val ev: Float = 0f,
    val isAutoFocus: Boolean = true,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f
)

@Serializable
data class DeviceHealth(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val temperatureCelsius: Float = 25f
)
