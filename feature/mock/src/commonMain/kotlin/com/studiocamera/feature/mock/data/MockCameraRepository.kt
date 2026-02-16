package com.studiocamera.feature.mock.data

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.repository.CameraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockCameraRepository : CameraRepository {

    private val _cameraState = MutableStateFlow(
        CameraState(
            iso = 100,
            shutterSpeed = "1/60",
            aperture = 2.8f,
            ev = 0f,
            isAutoFocus = true
        )
    )
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var photoCounter = 0
    private var videoCounter = 0

    override suspend fun capturePhoto(): ApiResult<String> {
        delay(200) // Simulate shutter
        photoCounter++
        return ApiResult.Success("mock_photo_$photoCounter")
    }

    override suspend fun startRecording(): ApiResult<Unit> {
        delay(100)
        _cameraState.value = _cameraState.value.copy(isRecording = true, recordingDurationMs = 0L)
        return ApiResult.Success(Unit)
    }

    override suspend fun stopRecording(): ApiResult<String> {
        delay(100)
        videoCounter++
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
        return ApiResult.Success("mock_video_$videoCounter")
    }

    override suspend fun getSettings(): ApiResult<CameraState> {
        delay(50)
        return ApiResult.Success(_cameraState.value)
    }

    override suspend fun updateSettings(
        iso: Int?,
        shutterSpeed: String?,
        aperture: Float?,
        ev: Float?,
        isAutoFocus: Boolean?
    ): ApiResult<CameraState> {
        delay(100)
        _cameraState.value = _cameraState.value.copy(
            iso = iso ?: _cameraState.value.iso,
            shutterSpeed = shutterSpeed ?: _cameraState.value.shutterSpeed,
            aperture = aperture ?: _cameraState.value.aperture,
            ev = ev ?: _cameraState.value.ev,
            isAutoFocus = isAutoFocus ?: _cameraState.value.isAutoFocus
        )
        return ApiResult.Success(_cameraState.value)
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> {
        delay(150)
        _cameraState.value = _cameraState.value.copy(
            focusX = x,
            focusY = y,
            isAutoFocus = false
        )
        return ApiResult.Success(Unit)
    }
}
