package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import kotlinx.coroutines.flow.StateFlow

interface CameraRepository {
    val cameraState: StateFlow<CameraState>

    suspend fun capturePhoto(): ApiResult<String>
    suspend fun startRecording(): ApiResult<Unit>
    suspend fun stopRecording(): ApiResult<String>
    suspend fun getSettings(): ApiResult<CameraState>
    suspend fun updateSettings(
        iso: Int? = null,
        shutterSpeed: String? = null,
        aperture: Float? = null,
        ev: Float? = null,
        isAutoFocus: Boolean? = null
    ): ApiResult<CameraState>
    suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit>
}
