package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class FlashMode { Off, Auto, On, RedEye }

@Serializable
enum class ImageFormat { JPEG, RAW, JPEG_RAW }

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
    val focusY: Float = 0.5f,
    val exposureMode: String = "P",
    val shootMode: String = "still",
    val whiteBalance: String = "Auto",
    val batteryPercent: Int = -1,
    val storageRemainingMb: Long = -1L,
    val valuesReported: Boolean = false,
    val flashMode: FlashMode = FlashMode.Off,
    val imageFormat: ImageFormat = ImageFormat.JPEG,
    val photoResolution: String = "L",
    val videoResolution: String = "4K",
    val videoFps: Int = 30,
    val hdrEnabled: Boolean = false,
    val availableIso: List<Int> = emptyList(),
    val availableShutterSpeed: List<String> = emptyList(),
    val availableAperture: List<Float> = emptyList(),
    val availableExposureMode: List<String> = emptyList(),
    val availableWhiteBalance: List<String> = emptyList(),
    val availableFlashModes: List<String> = emptyList(),
    val availableImageFormats: List<String> = emptyList(),
    val availablePhotoResolutions: List<String> = emptyList(),
    val availableVideoResolutions: List<String> = emptyList(),
    val availableVideoFps: List<Int> = emptyList(),
    val availableAspectRatios: List<String> = emptyList()
)

@Serializable
data class DeviceHealth(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val temperatureCelsius: Float = 25f
)
