package com.studiocamera.core.data.camera.stub

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Stub camera repository for brands whose remote API is not yet implemented.
 * Returns user-friendly error messages instead of crashing.
 */
class StubCameraRepository(
    private val brand: CameraBrand = CameraBrand.Unknown
) : CameraRepository {

    companion object {
        private const val TAG = "StubCamera"
    }

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val brandName: String get() = when (brand) {
        CameraBrand.Nikon -> "Nikon"
        CameraBrand.Fujifilm -> "Fujifilm"
        CameraBrand.OmSystem -> "OM System"
        else -> brand.name
    }

    override fun liveViewFrames(): Flow<ByteArray> {
        Logger.w(TAG) { "Live view not available for $brandName cameras" }
        return emptyFlow()
    }

    private fun unsupported(): ApiResult<Nothing> =
        ApiResult.Error(
            SessionError.Unknown(
                IllegalStateException(
                    "$brandName remote control is coming soon. " +
                        "Currently supported: Sony, Canon, Panasonic/Lumix."
                )
            )
        )

    override suspend fun initializeSession(): ApiResult<Unit> = unsupported()

    override suspend fun capturePhoto(): ApiResult<String> = unsupported()

    override suspend fun startRecording(): ApiResult<Unit> = unsupported()

    override suspend fun stopRecording(): ApiResult<String> = unsupported()

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = unsupported()

    override suspend fun getSettings(): ApiResult<CameraState> = unsupported()

    override suspend fun updateSettings(
        iso: Int?,
        shutterSpeed: String?,
        aperture: Float?,
        ev: Float?,
        isAutoFocus: Boolean?,
        flashMode: com.studiocamera.core.domain.model.FlashMode?,
        imageFormat: com.studiocamera.core.domain.model.ImageFormat?,
        photoResolution: String?,
        videoResolution: String?,
        videoFps: Int?,
        hdrEnabled: Boolean?,
        whiteBalance: String?,
        exposureMode: String?
    ): ApiResult<CameraState> = unsupported()

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = unsupported()
}
