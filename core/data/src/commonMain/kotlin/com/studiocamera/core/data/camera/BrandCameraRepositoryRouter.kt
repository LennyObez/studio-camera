package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.stub.StubCameraRepository
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class BrandCameraRepositoryRouter(
    private val connectionStateManager: ConnectionStateManager,
    private val brandRepositories: Map<CameraBrand, CameraRepository>
) : CameraRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun currentRepo(): CameraRepository {
        val brand = connectionStateManager.connectedDevice.value?.cameraBrand ?: CameraBrand.Unknown
        return brandRepositories[brand] ?: StubCameraRepository(brand)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val cameraState: StateFlow<CameraState> = connectionStateManager.connectedDevice
        .flatMapLatest { device ->
            val brand = device?.cameraBrand ?: CameraBrand.Unknown
            val repo = brandRepositories[brand] ?: StubCameraRepository(brand)
            repo.cameraState
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), CameraState())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun liveViewFrames(): Flow<ByteArray> {
        // React to device changes by switching the underlying flow
        return connectionStateManager.connectedDevice.flatMapLatest { device ->
            val brand = device?.cameraBrand ?: CameraBrand.Unknown
            val hasRepo = brandRepositories.containsKey(brand)
            Logger.i("CameraRouter") {
                "liveViewFrames: device=${device?.deviceName}, brand=$brand, hasRepo=$hasRepo"
            }
            val repo = brandRepositories[brand] ?: StubCameraRepository(brand)
            repo.liveViewFrames()
        }
    }

    override suspend fun initializeSession(): ApiResult<Unit> = currentRepo().initializeSession()

    override suspend fun capturePhoto(): ApiResult<String> = currentRepo().capturePhoto()

    override suspend fun startRecording(): ApiResult<Unit> = currentRepo().startRecording()

    override suspend fun stopRecording(): ApiResult<String> = currentRepo().stopRecording()

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = currentRepo().setShootMode(mode)

    override suspend fun getSettings(): ApiResult<CameraState> = currentRepo().getSettings()

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
    ): ApiResult<CameraState> = currentRepo().updateSettings(
        iso, shutterSpeed, aperture, ev, isAutoFocus,
        flashMode, imageFormat, photoResolution, videoResolution, videoFps, hdrEnabled,
        whiteBalance, exposureMode
    )

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = currentRepo().tapToFocus(x, y)
}
