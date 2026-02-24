package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.stub.StubCameraRepository
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.network.CircuitBreaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class BrandCameraRepositoryRouter(
    private val connectionStateManager: ConnectionStateManager,
    private val brandRepositories: Map<CameraBrand, CameraRepository>,
    private val circuitBreaker: CircuitBreaker,
    externalScope: CoroutineScope
) : CameraRepository {

    private val fallbackStub = StubCameraRepository()

    private fun currentRepo(): CameraRepository {
        val brand = connectionStateManager.connectedDevice.value?.cameraBrand ?: CameraBrand.Unknown
        return brandRepositories[brand] ?: fallbackStub
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val cameraState: StateFlow<CameraState> = connectionStateManager.connectedDevice
        .flatMapLatest { device ->
            val brand = device?.cameraBrand ?: CameraBrand.Unknown
            val repo = brandRepositories[brand] ?: fallbackStub
            repo.cameraState
        }
        .stateIn(externalScope, SharingStarted.WhileSubscribed(5000), CameraState())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun liveViewFrames(): Flow<ByteArray> {
        // React to device changes by switching the underlying flow
        return connectionStateManager.connectedDevice.flatMapLatest { device ->
            val brand = device?.cameraBrand ?: CameraBrand.Unknown
            val hasRepo = brandRepositories.containsKey(brand)
            Logger.i("CameraRouter") {
                "liveViewFrames: device=${device?.deviceName}, brand=$brand, hasRepo=$hasRepo"
            }
            val repo = brandRepositories[brand] ?: fallbackStub
            repo.liveViewFrames()
        }
    }

    /**
     * Wraps a delegated [ApiResult] call with the circuit breaker.
     * Fails fast with [SessionError.CircuitOpen] when the breaker is open.
     * Only retryable errors count towards tripping the breaker.
     */
    private suspend fun <T> withBreaker(block: suspend () -> ApiResult<T>): ApiResult<T> {
        if (!circuitBreaker.allowRequest()) {
            Logger.d("CameraRouter") { "Circuit open — failing fast" }
            return ApiResult.Error(SessionError.CircuitOpen)
        }
        val result = block()
        when (result) {
            is ApiResult.Success -> circuitBreaker.recordSuccess()
            is ApiResult.Error -> {
                if (result.error.isRetryable) circuitBreaker.recordFailure()
            }
        }
        return result
    }

    override suspend fun initializeSession(): ApiResult<Unit> = withBreaker { currentRepo().initializeSession() }

    override suspend fun capturePhoto(): ApiResult<String> = withBreaker { currentRepo().capturePhoto() }

    override suspend fun startRecording(): ApiResult<Unit> = withBreaker { currentRepo().startRecording() }

    override suspend fun stopRecording(): ApiResult<String> = withBreaker { currentRepo().stopRecording() }

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = withBreaker { currentRepo().setShootMode(mode) }

    override suspend fun getSettings(): ApiResult<CameraState> = withBreaker { currentRepo().getSettings() }

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
    ): ApiResult<CameraState> = withBreaker {
        currentRepo().updateSettings(
            iso, shutterSpeed, aperture, ev, isAutoFocus,
            flashMode, imageFormat, photoResolution, videoResolution, videoFps, hdrEnabled,
            whiteBalance, exposureMode
        )
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = withBreaker { currentRepo().tapToFocus(x, y) }
}
