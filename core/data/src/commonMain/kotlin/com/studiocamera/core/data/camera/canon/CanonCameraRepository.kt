package com.studiocamera.core.data.camera.canon

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.MjpegFrameExtractor
import kotlinx.coroutines.CancellationException
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.network.safeApiCall
import io.ktor.client.HttpClient
import com.studiocamera.core.domain.model.SessionError
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Canon CCAPI implementation.
 * Canon Camera Control API (CCAPI) uses REST endpoints.
 */
class CanonCameraRepository(
    private val httpClient: HttpClient,
    private val mjpegExtractor: MjpegFrameExtractor,
    private val endpoint: () -> String
) : CameraRepository {

    companion object {
        private const val TAG = "CanonCamera"
        private const val CCAPI_BASE = "/ccapi/ver100"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private fun ccapi(path: String): String = "${endpoint()}$CCAPI_BASE$path"

    override suspend fun initializeSession(): ApiResult<Unit> {
        val ep = endpoint()
        if (ep.isBlank()) {
            return ApiResult.Error(SessionError.DeviceUnreachable)
        }
        return try {
            // Verify connectivity by fetching device information
            val response = httpClient.get("$ep$CCAPI_BASE/deviceinformation")
            if (response.status.isSuccess()) {
                Logger.i(TAG) { "Canon CCAPI connectivity verified" }
            }
            // Fetch initial settings to populate state
            getSettings()
            _cameraState.value = _cameraState.value.copy(valuesReported = true)
            ApiResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "initializeSession failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
    }

    override fun liveViewFrames(): Flow<ByteArray> {
        val ep = endpoint()
        if (ep.isBlank()) return emptyFlow()

        val liveViewUrl = "$ep$CCAPI_BASE/shooting/liveview"
        return mjpegExtractor.streamFrames(liveViewUrl)
    }

    override suspend fun capturePhoto(): ApiResult<String> = safeApiCall {
        httpClient.post(ccapi("/shooting/control/shutterbutton")) {
            contentType(ContentType.Application.Json)
            setBody("""{"af": true}""")
        }
        Logger.d(TAG) { "Photo captured" }
        "captured"
    }

    override suspend fun startRecording(): ApiResult<Unit> = safeApiCall {
        httpClient.post(ccapi("/shooting/control/recbutton"))
        _cameraState.value = _cameraState.value.copy(isRecording = true)
        Logger.d(TAG) { "Recording started" }
    }

    override suspend fun stopRecording(): ApiResult<String> = safeApiCall {
        httpClient.post(ccapi("/shooting/control/recbutton")) // Toggle
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
        Logger.d(TAG) { "Recording stopped" }
        "recorded"
    }

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = safeApiCall {
        _cameraState.value = _cameraState.value.copy(shootMode = mode)
        Logger.d(TAG) { "Shoot mode set to $mode (stubbed for Canon)" }
    }

    override suspend fun getSettings(): ApiResult<CameraState> = safeApiCall {
        var state = _cameraState.value

        try {
            val isoResp = httpClient.get(ccapi("/shooting/settings/iso"))
            parseSettingValue(isoResp.bodyAsText())?.toIntOrNull()?.let {
                state = state.copy(iso = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read ISO: ${e.message}" }
        }

        try {
            val tvResp = httpClient.get(ccapi("/shooting/settings/tv"))
            parseSettingValue(tvResp.bodyAsText())?.let {
                state = state.copy(shutterSpeed = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read shutter speed: ${e.message}" }
        }

        try {
            val avResp = httpClient.get(ccapi("/shooting/settings/av"))
            parseSettingValue(avResp.bodyAsText())?.toFloatOrNull()?.let {
                state = state.copy(aperture = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read aperture: ${e.message}" }
        }

        try {
            val evResp = httpClient.get(ccapi("/shooting/settings/exposure"))
            parseSettingValue(evResp.bodyAsText())?.toFloatOrNull()?.let {
                state = state.copy(ev = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read EV: ${e.message}" }
        }

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
        var state = _cameraState.value

        iso?.let {
            httpClient.put(ccapi("/shooting/settings/iso")) {
                contentType(ContentType.Application.Json)
                setBody("""{"value": "$it"}""")
            }
            state = state.copy(iso = it)
        }

        shutterSpeed?.let {
            httpClient.put(ccapi("/shooting/settings/tv")) {
                contentType(ContentType.Application.Json)
                setBody("""{"value": "$it"}""")
            }
            state = state.copy(shutterSpeed = it)
        }

        aperture?.let {
            val formatted = if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it)
            httpClient.put(ccapi("/shooting/settings/av")) {
                contentType(ContentType.Application.Json)
                setBody("""{"value": "$formatted"}""")
            }
            state = state.copy(aperture = it)
        }

        ev?.let {
            val evStr = if (it >= 0) "+%.1f".format(it) else "%.1f".format(it)
            httpClient.put(ccapi("/shooting/settings/exposure")) {
                contentType(ContentType.Application.Json)
                setBody("""{"value": "$evStr"}""")
            }
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
                httpClient.put(ccapi("/shooting/settings/wb")) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"value": "$it"}""")
                }
            } catch (_: Exception) {}
            state = state.copy(whiteBalance = it)
        }

        exposureMode?.let {
            try {
                httpClient.put(ccapi("/shooting/settings/shootingmode")) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"value": "$it"}""")
                }
            } catch (_: Exception) {}
            state = state.copy(exposureMode = it)
        }

        _cameraState.value = state
        state
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = safeApiCall {
        httpClient.post(ccapi("/shooting/control/af")) {
            contentType(ContentType.Application.Json)
            setBody("""{"x": ${(x * 100).toInt()}, "y": ${(y * 100).toInt()}}""")
        }
        _cameraState.value = _cameraState.value.copy(focusX = x, focusY = y)
        Logger.d(TAG) { "AF at ($x, $y)" }
    }

    private fun parseSettingValue(jsonText: String): String? {
        return try {
            val obj = json.decodeFromString<JsonObject>(jsonText)
            obj["value"]?.let { element ->
                if (element is JsonPrimitive) element.content else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
