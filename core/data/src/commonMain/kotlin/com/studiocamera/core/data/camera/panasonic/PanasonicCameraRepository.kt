package com.studiocamera.core.data.camera.panasonic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.network.safeApiCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * Panasonic/Lumix camera control via cam.cgi HTTP interface.
 *
 * All commands are GET requests with query parameters.
 * Live view uses UDP streaming on port 49152 (requires platform-specific socket).
 */
class PanasonicCameraRepository(
    private val httpClient: HttpClient,
    private val endpoint: () -> String
) : CameraRepository {

    companion object {
        private const val TAG = "PanasonicCamera"
    }

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private fun cgi(params: String): String = "${endpoint()}/cam.cgi?$params"

    override suspend fun initializeSession(): ApiResult<Unit> {
        val ep = endpoint()
        if (ep.isBlank()) {
            return ApiResult.Error(SessionError.DeviceUnreachable)
        }
        return try {
            // Verify connectivity by querying camera capabilities
            val response = httpClient.get("$ep/cam.cgi?mode=getinfo&type=capability")
            if (response.status.isSuccess()) {
                Logger.i(TAG) { "Panasonic cam.cgi connectivity verified" }
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

        return flow<ByteArray> {
            try {
                httpClient.get(cgi("mode=startstream&value=49152"))
                Logger.d(TAG) { "Panasonic stream started on port 49152" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to start Panasonic stream: ${e.message}" }
            }
            // UDP live view requires platform-specific DatagramSocket (not yet implemented).
            // Log a warning and return an empty flow rather than crashing.
            Logger.w(TAG) { "Panasonic UDP live view not yet implemented — live view unavailable" }
        }.onCompletion {
            try {
                httpClient.get(cgi("mode=stopstream"))
            } catch (_: Exception) {}
        }
    }

    override suspend fun capturePhoto(): ApiResult<String> = safeApiCall {
        httpClient.get(cgi("mode=camcmd&value=capture"))
        Logger.d(TAG) { "Photo captured" }
        "captured"
    }

    override suspend fun startRecording(): ApiResult<Unit> = safeApiCall {
        httpClient.get(cgi("mode=camcmd&value=video_recstart"))
        _cameraState.value = _cameraState.value.copy(isRecording = true)
        Logger.d(TAG) { "Recording started" }
    }

    override suspend fun stopRecording(): ApiResult<String> = safeApiCall {
        httpClient.get(cgi("mode=camcmd&value=video_recstop"))
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
        Logger.d(TAG) { "Recording stopped" }
        "recorded"
    }

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = safeApiCall {
        _cameraState.value = _cameraState.value.copy(shootMode = mode)
        Logger.d(TAG) { "Shoot mode set to $mode (stubbed for Panasonic)" }
    }

    override suspend fun getSettings(): ApiResult<CameraState> = safeApiCall {
        var state = _cameraState.value

        try {
            val isoResp = httpClient.get(cgi("mode=getsetting&type=iso"))
            parseXmlValue(isoResp.bodyAsText())?.toIntOrNull()?.let {
                state = state.copy(iso = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read ISO: ${e.message}" }
        }

        try {
            val ssResp = httpClient.get(cgi("mode=getsetting&type=shtrspeed"))
            parseXmlValue(ssResp.bodyAsText())?.let {
                state = state.copy(shutterSpeed = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read shutter speed: ${e.message}" }
        }

        try {
            val avResp = httpClient.get(cgi("mode=getsetting&type=fnumber"))
            parseXmlValue(avResp.bodyAsText())?.toFloatOrNull()?.let {
                state = state.copy(aperture = it)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read aperture: ${e.message}" }
        }

        try {
            val evResp = httpClient.get(cgi("mode=getsetting&type=exposure"))
            parseXmlValue(evResp.bodyAsText())?.toFloatOrNull()?.let {
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
            httpClient.get(cgi("mode=setsetting&type=iso&value=$it"))
            state = state.copy(iso = it)
        }

        shutterSpeed?.let {
            httpClient.get(cgi("mode=setsetting&type=shtrspeed&value=$it"))
            state = state.copy(shutterSpeed = it)
        }

        aperture?.let {
            httpClient.get(cgi("mode=setsetting&type=fnumber&value=$it"))
            state = state.copy(aperture = it)
        }

        ev?.let {
            httpClient.get(cgi("mode=setsetting&type=exposure&value=$it"))
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
                httpClient.get(cgi("mode=setsetting&type=whitebalance&value=$it"))
            } catch (_: Exception) {}
            state = state.copy(whiteBalance = it)
        }

        exposureMode?.let {
            try {
                httpClient.get(cgi("mode=setsetting&type=shootmode&value=$it"))
            } catch (_: Exception) {}
            state = state.copy(exposureMode = it)
        }

        _cameraState.value = state
        state
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = safeApiCall {
        // Panasonic uses absolute pixel coordinates based on OSD size
        // Normalize to Panasonic's coordinate range (typically 1000x1000)
        val pX = (x * 1000).toInt().coerceIn(0, 1000)
        val pY = (y * 1000).toInt().coerceIn(0, 1000)
        httpClient.get(cgi("mode=camctrl&type=touch&value=${pX}%2F${pY}&value2=on"))
        _cameraState.value = _cameraState.value.copy(focusX = x, focusY = y)
        Logger.d(TAG) { "Touch AF at ($pX, $pY)" }
    }

    /**
     * Panasonic cam.cgi responses are XML. Extract the value from a simple XML response.
     * Format: <camrply><result>ok</result><settingvalue>VALUE</settingvalue></camrply>
     */
    private fun parseXmlValue(xml: String): String? {
        for (tag in listOf("settingvalue", "curvalue")) {
            val start = xml.indexOf("<$tag>")
            val end = xml.indexOf("</$tag>")
            if (start >= 0 && end > start) {
                return xml.substring(start + "<$tag>".length, end).trim()
            }
        }
        return null
    }
}
