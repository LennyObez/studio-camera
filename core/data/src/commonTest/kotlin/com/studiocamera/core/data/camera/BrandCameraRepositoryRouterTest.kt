package com.studiocamera.core.data.camera

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.network.CircuitBreaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrandCameraRepositoryRouterTest {

    private fun device(brand: CameraBrand) = PairedDevice(
        deviceId = "test-${brand.name}",
        deviceName = "Test ${brand.name}",
        endpoint = "http://192.168.1.1",
        fingerprint = "AA:BB:CC",
        cameraBrand = brand
    )

    private class FakeCameraRepository(
        val brand: CameraBrand
    ) : CameraRepository {
        var capturePhotoCalled = false
        var initCalled = false

        private val _cameraState = MutableStateFlow(CameraState())
        override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

        override fun liveViewFrames(): Flow<ByteArray> = emptyFlow()

        override suspend fun initializeSession(): ApiResult<Unit> {
            initCalled = true
            return ApiResult.Success(Unit)
        }

        override suspend fun capturePhoto(): ApiResult<String> {
            capturePhotoCalled = true
            return ApiResult.Success("${brand.name}-photo-001")
        }

        override suspend fun startRecording() = ApiResult.Success(Unit)
        override suspend fun stopRecording() = ApiResult.Success("video-001")
        override suspend fun setShootMode(mode: String) = ApiResult.Success(Unit)
        override suspend fun getSettings() = ApiResult.Success(CameraState())
        override suspend fun updateSettings(
            iso: Int?, shutterSpeed: String?, aperture: Float?, ev: Float?,
            isAutoFocus: Boolean?, flashMode: com.studiocamera.core.domain.model.FlashMode?,
            imageFormat: com.studiocamera.core.domain.model.ImageFormat?,
            photoResolution: String?, videoResolution: String?, videoFps: Int?,
            hdrEnabled: Boolean?, whiteBalance: String?, exposureMode: String?
        ) = ApiResult.Success(CameraState())
        override suspend fun tapToFocus(x: Float, y: Float) = ApiResult.Success(Unit)
    }

    private data class TestHarness(
        val router: BrandCameraRepositoryRouter,
        val connectionStateManager: ConnectionStateManager,
        val sonyRepo: FakeCameraRepository,
        val canonRepo: FakeCameraRepository,
        val scope: TestScope
    )

    private fun createHarness(): TestHarness {
        val scope = TestScope(UnconfinedTestDispatcher())
        val connectionStateManager = ConnectionStateManager()
        val sonyRepo = FakeCameraRepository(CameraBrand.Sony)
        val canonRepo = FakeCameraRepository(CameraBrand.Canon)

        val router = BrandCameraRepositoryRouter(
            connectionStateManager = connectionStateManager,
            brandRepositories = mapOf(
                CameraBrand.Sony to sonyRepo,
                CameraBrand.Canon to canonRepo
            ),
            circuitBreaker = CircuitBreaker(),
            externalScope = scope
        )

        return TestHarness(router, connectionStateManager, sonyRepo, canonRepo, scope)
    }

    @Test
    fun capturePhoto_delegatesToSonyRepo_whenSonyDeviceConnected() = runTest {
        val h = createHarness()
        h.connectionStateManager.setConnectedDevice(device(CameraBrand.Sony))
        h.scope.advanceUntilIdle()

        val result = h.router.capturePhoto()

        assertTrue(h.sonyRepo.capturePhotoCalled)
        assertIs<ApiResult.Success<String>>(result)
        assertEquals("Sony-photo-001", result.data)
    }

    @Test
    fun capturePhoto_delegatesToCanonRepo_whenCanonDeviceConnected() = runTest {
        val h = createHarness()
        h.connectionStateManager.setConnectedDevice(device(CameraBrand.Canon))
        h.scope.advanceUntilIdle()

        val result = h.router.capturePhoto()

        assertTrue(h.canonRepo.capturePhotoCalled)
        assertIs<ApiResult.Success<String>>(result)
        assertEquals("Canon-photo-001", result.data)
    }

    @Test
    fun capturePhoto_returnsError_whenNoDeviceConnected() = runTest {
        val h = createHarness()
        h.scope.advanceUntilIdle()

        val result = h.router.capturePhoto()

        // Falls back to StubCameraRepository which returns Error
        assertIs<ApiResult.Error>(result)
    }

    @Test
    fun capturePhoto_returnsError_whenUnknownBrandConnected() = runTest {
        val h = createHarness()
        h.connectionStateManager.setConnectedDevice(device(CameraBrand.Unknown))
        h.scope.advanceUntilIdle()

        val result = h.router.capturePhoto()

        assertIs<ApiResult.Error>(result)
    }

    @Test
    fun initializeSession_delegatesToCorrectBrandRepo() = runTest {
        val h = createHarness()
        h.connectionStateManager.setConnectedDevice(device(CameraBrand.Sony))
        h.scope.advanceUntilIdle()

        h.router.initializeSession()

        assertTrue(h.sonyRepo.initCalled)
    }

    @Test
    fun switchingDevice_changesActiveRepo() = runTest {
        val h = createHarness()

        h.connectionStateManager.setConnectedDevice(device(CameraBrand.Sony))
        h.scope.advanceUntilIdle()
        h.router.capturePhoto()
        assertTrue(h.sonyRepo.capturePhotoCalled)

        h.connectionStateManager.setConnectedDevice(device(CameraBrand.Canon))
        h.scope.advanceUntilIdle()
        h.router.capturePhoto()
        assertTrue(h.canonRepo.capturePhotoCalled)
    }
}
