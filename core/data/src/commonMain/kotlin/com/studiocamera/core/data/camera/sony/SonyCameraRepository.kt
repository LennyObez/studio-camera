package com.studiocamera.core.data.camera.sony

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.network.safeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sony Camera Remote API implementation.
 * Uses JSON-RPC over HTTP to control Sony cameras.
 *
 * Endpoint format: http://<gateway>:8080/sony/camera
 */
class SonyCameraRepository(
    private val apiClient: SonyApiClient,
    private val liveViewDecoder: SonyLiveViewDecoder,
    private val endpoint: () -> String
) : CameraRepository {

    companion object {
        private const val TAG = "SonyCamera"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    @Volatile
    private var liveViewUrl: String? = null
    @Volatile
    private var eventPollingActive = false
    @Volatile
    private var eventPollingJob: Job? = null
    @Volatile
    private var sessionInitialized = false

    override suspend fun initializeSession(): ApiResult<Unit> {
        val ep = endpoint()
        if (ep.isBlank()) {
            Logger.w(TAG) { "initializeSession() called with blank endpoint" }
            return ApiResult.Error(
                com.studiocamera.core.domain.model.SessionError.DeviceUnreachable
            )
        }
        return try {
            // Sony cameras require startRecMode before any shooting commands work
            // Or setCameraFunction("RemoteShooting") on newer models
            try {
                apiClient.call(ep, "setCameraFunction", listOf(JsonPrimitive("RemoteShooting")))
                Logger.i(TAG) { "setCameraFunction RemoteShooting succeeded" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.d(TAG) { "setCameraFunction RemoteShooting not needed or failed: ${e.message}" }
            }

            try {
                apiClient.call(ep, "startRecMode")
                Logger.i(TAG) { "startRecMode succeeded" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Some newer cameras don't support startRecMode — that's OK
                Logger.d(TAG) { "startRecMode not needed or failed: ${e.message}" }
            }

            // Verify API connectivity by fetching available API list
            try {
                val result = apiClient.callForResults(ep, "getAvailableApiList")
                Logger.i(TAG) { "getAvailableApiList returned ${result?.size ?: 0} entries" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.d(TAG) { "getAvailableApiList failed (non-fatal): ${e.message}" }
            }

            // Fetch initial settings to populate cameraState with real values
            fetchInitialSettings(ep)

            sessionInitialized = true
            ApiResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "initializeSession failed" }
            ApiResult.Error(
                com.studiocamera.core.domain.model.SessionError.Unknown(e)
            )
        }
    }

    private suspend fun ensureRecMode() {
        if (!sessionInitialized) {
            Logger.d(TAG) { "ensureRecMode: session not initialized, calling initializeSession()" }
            initializeSession()
        }
    }

    private suspend fun fetchInitialSettings(ep: String) {
        var state = _cameraState.value
        try {
            val params = listOf(JsonPrimitive(false))
            val results = apiClient.callForResults(ep, "getEvent", params)
            if (results != null) {
                parseEventResults(results)
                state = _cameraState.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.d(TAG) { "getEvent for initial settings failed: ${e.message}" }
        }
        _cameraState.value = state.copy(valuesReported = true)
        Logger.i(TAG) { "Initial settings fetched, valuesReported=true" }
    }

    override fun liveViewFrames(): Flow<ByteArray> {
        val ep = endpoint()
        Logger.i(TAG) { "liveViewFrames() called — endpoint: '$ep'" }
        if (ep.isBlank()) {
            Logger.w(TAG) { "liveViewFrames() called with blank endpoint — no connected camera?" }
            return emptyFlow()
        }

        return kotlinx.coroutines.flow.flow {
            // Initialize if not already done (safety net — SessionManager should call first)
            if (!sessionInitialized) {
                initializeSession()
            }

            // Start live view and get the stream URL
            val url = startLiveView(ep)
            if (url == null) {
                Logger.e(TAG) { "startLiveview returned no URL — live view cannot start" }
                return@flow
            }

            // Start event polling for camera state updates
            startEventPolling(ep)

            Logger.i(TAG) { "Beginning frame decode from $url" }
            var frameCount = 0

            // Decode and emit frames from the Sony proprietary format
            liveViewDecoder.streamFrames(url).collect { frame ->
                frameCount++
                if (frameCount == 1) {
                    Logger.i(TAG) { "First live view frame received (${frame.size} bytes)" }
                }
                emit(frame)
            }

            Logger.i(TAG) { "Live view stream ended after $frameCount frames" }
        }.onCompletion { cause ->
            Logger.i(TAG) { "liveViewFrames flow completed (cause: ${cause?.message ?: "normal"})" }
            // Stop event polling when the live view flow ends (user navigated away, etc.)
            stopEventPolling()
        }
    }

    private fun stopEventPolling() {
        eventPollingActive = false
        eventPollingJob?.cancel()
        eventPollingJob = null
        Logger.d(TAG) { "Event polling stopped" }
    }

    private suspend fun startLiveView(ep: String): String? {
        return try {
            val result = apiClient.callForResult(ep, "startLiveview")
            val url = result?.jsonPrimitive?.content
            liveViewUrl = url
            Logger.i(TAG) { "Live view started at $url" }
            url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to start live view" }
            null
        }
    }

    private fun startEventPolling(ep: String) {
        if (eventPollingActive) return
        eventPollingActive = true
        eventPollingJob = scope.launch {
            var longPolling = false
            while (isActive && eventPollingActive) {
                try {
                    val params = listOf(JsonPrimitive(longPolling))
                    val results = apiClient.callForResults(ep, "getEvent", params)
                    if (results != null) {
                        parseEventResults(results)
                    }
                    longPolling = true // After first call, use long-polling
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    Logger.w(TAG, e) { "Event polling error: ${e.message}" }
                    delay(2000)
                    longPolling = false
                }
            }
        }
    }

    private fun parseEventResults(results: kotlinx.serialization.json.JsonArray) {
        try {
            var state = _cameraState.value

            // Sony Camera Remote API v2.4 getEvent result indices:
            // Index 1: camera status
            // Index 2: live view status
            // Index 12: exposure mode
            // Index 16: white balance
            // Index 25: exposure compensation
            // Index 27: f-number
            // Index 29: ISO speed rate
            // Index 32: shutter speed

            // Exposure mode (index 12)
            results.getOrNull(12)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentExposureMode"]?.jsonPrimitive?.content?.let { mode ->
                        state = state.copy(exposureMode = mode)
                    }
                    element["exposureModeCandidates"]?.jsonArray?.let { candidates ->
                        val list = candidates.map { it.jsonPrimitive.content }
                        state = state.copy(availableExposureMode = list)
                    }
                }
            }

            // White balance (index 16)
            results.getOrNull(16)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentWhiteBalanceMode"]?.jsonPrimitive?.content?.let { wb ->
                        state = state.copy(whiteBalance = wb)
                    }
                    element["whiteBalanceCandidates"]?.jsonArray?.let { candidates ->
                        val list = candidates.map { it.jsonPrimitive.content }
                        state = state.copy(availableWhiteBalance = list)
                    }
                }
            }

            // Shoot mode (index 21)
            results.getOrNull(21)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentShootMode"]?.jsonPrimitive?.content?.let { sm ->
                        state = state.copy(shootMode = sm)
                    }
                }
            }

            // Exposure compensation (index 25) — Sony returns step index (1/3 EV steps)
            results.getOrNull(25)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentExposureCompensation"]?.jsonPrimitive?.content?.let { ev ->
                        val stepIndex = ev.toIntOrNull()
                        if (stepIndex != null) state = state.copy(ev = stepIndex / 3.0f)
                    }
                }
            }

            // F-number (index 27)
            results.getOrNull(27)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentFNumber"]?.jsonPrimitive?.content?.let { fn ->
                        val aperture = fn.removePrefix("F").toFloatOrNull()
                        if (aperture != null) state = state.copy(aperture = aperture)
                    }
                    element["fNumberCandidates"]?.jsonArray?.let { candidates ->
                        val list = candidates.mapNotNull { it.jsonPrimitive.content.removePrefix("F").toFloatOrNull() }
                        state = state.copy(availableAperture = list)
                    }
                }
            }

            // ISO speed rate (index 29)
            results.getOrNull(29)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentIsoSpeedRate"]?.jsonPrimitive?.content?.let { iso ->
                        val isoInt = iso.toIntOrNull()
                        if (isoInt != null) state = state.copy(iso = isoInt)
                    }
                    element["isoSpeedRateCandidates"]?.jsonArray?.let { candidates ->
                        val list = candidates.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                        state = state.copy(availableIso = list)
                    }
                }
            }

            // Shutter speed (index 32)
            results.getOrNull(32)?.let { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element["currentShutterSpeed"]?.jsonPrimitive?.content?.let { ss ->
                        state = state.copy(shutterSpeed = ss)
                    }
                    element["shutterSpeedCandidates"]?.jsonArray?.let { candidates ->
                        val list = candidates.map { it.jsonPrimitive.content }
                        state = state.copy(availableShutterSpeed = list)
                    }
                }
            }

            // Fallback iterator for dynamically hunting other candidates regardless of model-specific index shifts
            results.forEach { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    // Flash Mode
                    element["flashModeCandidates"]?.jsonArray?.let { candidates ->
                        state = state.copy(availableFlashModes = candidates.map { it.jsonPrimitive.content })
                    }
                    element["currentFlashMode"]?.jsonPrimitive?.content?.let { mode ->
                        val flashModeEnum = when (mode) {
                            "auto" -> com.studiocamera.core.domain.model.FlashMode.Auto
                            "on", "fill-flash" -> com.studiocamera.core.domain.model.FlashMode.On
                            "red-eye" -> com.studiocamera.core.domain.model.FlashMode.RedEye
                            else -> com.studiocamera.core.domain.model.FlashMode.Off
                        }
                        state = state.copy(flashMode = flashModeEnum)
                    }

                    // Still Size (Aspect Ratio & Photo Resolution)
                    element["stillSizeCandidates"]?.jsonArray?.let { candidates ->
                        val aspectRatios = mutableSetOf<String>()
                        val sizes = mutableSetOf<String>()
                        candidates.forEach { c ->
                            if (c is kotlinx.serialization.json.JsonObject) {
                                c["aspectRatio"]?.jsonPrimitive?.content?.let { aspectRatios.add(it) }
                                c["size"]?.jsonPrimitive?.content?.let { sizes.add(it) }
                            }
                        }
                        if (aspectRatios.isNotEmpty()) state = state.copy(availableAspectRatios = aspectRatios.toList())
                        if (sizes.isNotEmpty()) state = state.copy(availablePhotoResolutions = sizes.toList())
                    }

                    // Image Format (Still Quality)
                    element["stillQualityCandidates"]?.jsonArray?.let { candidates ->
                        state = state.copy(availableImageFormats = candidates.map { it.jsonPrimitive.content })
                    }

                    // Video Qualities (we use this to populate Video Resolutions/FPS drop-downs dynamically)
                    element["movieQualityCandidates"]?.jsonArray?.let { candidates ->
                        state = state.copy(availableVideoResolutions = candidates.map { it.jsonPrimitive.content })
                    }
                }
            }

            _cameraState.value = state.copy(valuesReported = true)
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to parse event results: ${e.message}" }
        }
    }

    override suspend fun capturePhoto(): ApiResult<String> = safeApiCall {
        ensureRecMode()
        val result = apiClient.callForResult(endpoint(), "actTakePicture")
        val urls = result?.jsonArray
        val photoUrl = urls?.firstOrNull()?.jsonPrimitive?.content ?: "captured"
        Logger.d(TAG) { "Photo captured: $photoUrl" }
        photoUrl
    }

    override suspend fun startRecording(): ApiResult<Unit> = safeApiCall {
        ensureRecMode()
        apiClient.call(endpoint(), "startMovieRec")
        _cameraState.value = _cameraState.value.copy(isRecording = true)
        Logger.d(TAG) { "Recording started" }
    }

    override suspend fun stopRecording(): ApiResult<String> = safeApiCall {
        ensureRecMode()
        val result = apiClient.callForResult(endpoint(), "stopMovieRec")
        val thumbnailUrl = result?.jsonPrimitive?.content ?: "recorded"
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
        Logger.d(TAG) { "Recording stopped: $thumbnailUrl" }
        thumbnailUrl
    }

    override suspend fun getSettings(): ApiResult<CameraState> = safeApiCall {
        ensureRecMode()
        val ep = endpoint()
        var state = _cameraState.value

        try {
            val isoResult = apiClient.callForResult(ep, "getIsoSpeedRate")
            isoResult?.jsonPrimitive?.content?.toIntOrNull()?.let {
                state = state.copy(iso = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {}

        try {
            val ssResult = apiClient.callForResult(ep, "getShutterSpeed")
            ssResult?.jsonPrimitive?.content?.let {
                state = state.copy(shutterSpeed = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {}

        try {
            val fnResult = apiClient.callForResult(ep, "getFNumber")
            fnResult?.jsonPrimitive?.content?.removePrefix("F")?.toFloatOrNull()?.let {
                state = state.copy(aperture = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {}

        try {
            val evResult = apiClient.callForResult(ep, "getExposureCompensation")
            evResult?.jsonPrimitive?.content?.toFloatOrNull()?.let {
                state = state.copy(ev = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {}

        _cameraState.value = state.copy(valuesReported = true)
        state.copy(valuesReported = true)
    }

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
    ): ApiResult<CameraState> = safeApiCall {
        ensureRecMode()
        val ep = endpoint()
        var state = _cameraState.value

        iso?.let {
            apiClient.call(ep, "setIsoSpeedRate", listOf(JsonPrimitive(it.toString())))
            state = state.copy(iso = it)
        }

        shutterSpeed?.let {
            apiClient.call(ep, "setShutterSpeed", listOf(JsonPrimitive(it)))
            state = state.copy(shutterSpeed = it)
        }

        aperture?.let {
            apiClient.call(ep, "setFNumber", listOf(JsonPrimitive("F%.1f".format(it))))
            state = state.copy(aperture = it)
        }

        ev?.let {
            // Sony expects EV step index (1/3 EV steps): +1.0 EV = 3, -0.3 EV = -1
            val stepIndex = (it * 3).toInt()
            apiClient.call(ep, "setExposureCompensation", listOf(JsonPrimitive(stepIndex)))
            state = state.copy(ev = it)
        }

        isAutoFocus?.let {
            state = state.copy(isAutoFocus = it)
        }

        flashMode?.let { state = state.copy(flashMode = it) }
        imageFormat?.let { state = state.copy(imageFormat = it) }
        photoResolution?.let { state = state.copy(photoResolution = it) }
        videoResolution?.let { state = state.copy(videoResolution = it) }
        videoFps?.let { state = state.copy(videoFps = it) }
        hdrEnabled?.let { state = state.copy(hdrEnabled = it) }

        whiteBalance?.let {
            try {
                apiClient.call(ep, "setWhiteBalance", listOf(JsonPrimitive(it)))
                state = state.copy(whiteBalance = it)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to set white balance: ${e.message}" }
            }
        }

        exposureMode?.let {
            try {
                apiClient.call(ep, "setExposureMode", listOf(JsonPrimitive(it)))
                state = state.copy(exposureMode = it)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to set exposure mode: ${e.message}" }
            }
        }

        _cameraState.value = state
        state
    }

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = safeApiCall {
        ensureRecMode()
        val ep = endpoint()
        try {
            apiClient.call(ep, "setShootMode", listOf(JsonPrimitive(mode)))
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to setShootMode to $mode: ${e.message}" }
            throw e
        }
        _cameraState.value = _cameraState.value.copy(shootMode = mode)
        Logger.d(TAG) { "Shoot mode set to $mode" }
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = safeApiCall {
        ensureRecMode()
        // Sony uses percentage coordinates (0-100 range for x, 0-100 for y)
        val sonyX = (x * 100).toInt().coerceIn(0, 100)
        val sonyY = (y * 100).toInt().coerceIn(0, 100)
        apiClient.call(
            endpoint(),
            "setTouchAFPosition",
            listOf(JsonPrimitive(sonyX.toDouble()), JsonPrimitive(sonyY.toDouble()))
        )
        _cameraState.value = _cameraState.value.copy(focusX = x, focusY = y)
        Logger.d(TAG) { "Touch AF at ($sonyX, $sonyY)" }
    }
}
