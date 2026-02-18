package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.FlashMode
import com.studiocamera.core.domain.model.ImageFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CameraRepository {
    val cameraState: StateFlow<CameraState>

    suspend fun initializeSession(): ApiResult<Unit>

    fun liveViewFrames(): Flow<ByteArray>

    suspend fun capturePhoto(): ApiResult<String>
    suspend fun startRecording(): ApiResult<Unit>
    suspend fun stopRecording(): ApiResult<String>
    suspend fun getSettings(): ApiResult<CameraState>
    suspend fun updateSettings(
        iso: Int? = null,
        shutterSpeed: String? = null,
        aperture: Float? = null,
        ev: Float? = null,
        isAutoFocus: Boolean? = null,
        flashMode: FlashMode? = null,
        imageFormat: ImageFormat? = null,
        photoResolution: String? = null,
        videoResolution: String? = null,
        videoFps: Int? = null,
        hdrEnabled: Boolean? = null,
        whiteBalance: String? = null,
        exposureMode: String? = null
    ): ApiResult<CameraState>
    suspend fun setShootMode(mode: String): ApiResult<Unit>
    suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit>
}
