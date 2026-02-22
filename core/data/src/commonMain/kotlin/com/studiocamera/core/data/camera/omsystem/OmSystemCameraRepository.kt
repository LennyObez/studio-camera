package com.studiocamera.core.data.camera.omsystem

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.MjpegFrameExtractor
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.network.safeApiCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * OM System (Olympus) camera control via OPC (Open Platform Camera) HTTP interface.
 *
 * OM System cameras expose an HTTP interface at `http://192.168.0.10/` with
 * CGI endpoints for camera control. The protocol uses XML responses.
 *
 * Live view is served as MJPEG over HTTP at `/liveview/liveviewimg`.
 */
class OmSystemCameraRepository(
    private val httpClient: HttpClient,
    private val mjpegExtractor: MjpegFrameExtractor,
    private val endpoint: () -> String
) : CameraRepository {

    companion object {
        private const val TAG = "OmSystemCamera"
    }

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    override suspend fun initializeSession(): ApiResult<Unit> {
        val ep = endpoint()
        if (ep.isBlank()) {
            return ApiResult.Error(SessionError.DeviceUnreachable)
        }
        return try {
            val response = httpClient.get("$ep/get_caminfo.cgi")
            if (response.status.isSuccess()) {
                Logger.i(TAG) { "OM System OPC connectivity verified" }
            }
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
        return mjpegExtractor.streamFrames("$ep/liveview/liveviewimg")
    }

    override suspend fun capturePhoto(): ApiResult<String> = safeApiCall {
        val ep = endpoint()
        // Two-stage shutter: half-press (AF) + full release
        httpClient.get("$ep/exec_shutter.cgi?com=1st2ndpush")
        httpClient.get("$ep/exec_shutter.cgi?com=2nd1strelease")
        Logger.d(TAG) { "Photo captured" }
        "captured"
    }

    override suspend fun startRecording(): ApiResult<Unit> = safeApiCall {
        val ep = endpoint()
        httpClient.get("$ep/exec_takemotion.cgi?com=startrec")
        _cameraState.value = _cameraState.value.copy(isRecording = true)
        Logger.d(TAG) { "Recording started" }
    }

    override suspend fun stopRecording(): ApiResult<String> = safeApiCall {
        val ep = endpoint()
        httpClient.get("$ep/exec_takemotion.cgi?com=stoprec")
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
        Logger.d(TAG) { "Recording stopped" }
        "recorded"
    }

    override suspend fun setShootMode(mode: String): ApiResult<Unit> = safeApiCall {
        val ep = endpoint()
        // OM System allows setting take mode via camprop
        setCamProp(ep, "takemode", mode)
        _cameraState.value = _cameraState.value.copy(shootMode = mode)
        Logger.d(TAG) { "Shoot mode updated to $mode" }
    }

    override suspend fun getSettings(): ApiResult<CameraState> = safeApiCall {
        val ep = endpoint()
        var state = _cameraState.value

        getCamProp(ep, "iso")?.toIntOrNull()?.let {
            state = state.copy(iso = it)
        }
        getCamProp(ep, "shutspeedvalue")?.let {
            state = state.copy(shutterSpeed = it)
        }
        getCamProp(ep, "focalvalue")?.toFloatOrNull()?.let {
            state = state.copy(aperture = it)
        }
        getCamProp(ep, "expcomp")?.toFloatOrNull()?.let {
            state = state.copy(ev = it)
        }
        getCamProp(ep, "wbvalue")?.let {
            state = state.copy(whiteBalance = it)
        }
        getCamProp(ep, "takemode")?.let {
            state = state.copy(exposureMode = it)
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
        val ep = endpoint()
        var state = _cameraState.value

        iso?.let {
            setCamProp(ep, "iso", it.toString())
            state = state.copy(iso = it)
        }
        shutterSpeed?.let {
            setCamProp(ep, "shutspeedvalue", it)
            state = state.copy(shutterSpeed = it)
        }
        aperture?.let {
            setCamProp(ep, "focalvalue", it.toString())
            state = state.copy(aperture = it)
        }
        ev?.let {
            setCamProp(ep, "expcomp", it.toString())
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
                setCamProp(ep, "wbvalue", it)
                state = state.copy(whiteBalance = it)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to set white balance: ${e.message}" }
            }
        }

        exposureMode?.let {
            try {
                setCamProp(ep, "takemode", it)
                state = state.copy(exposureMode = it)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to set exposure mode: ${e.message}" }
            }
        }

        _cameraState.value = state
        state
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = safeApiCall {
        val ep = endpoint()
        // OM System uses touch AF with normalized coordinates
        val pX = (x * 1000).toInt().coerceIn(0, 1000)
        val pY = (y * 1000).toInt().coerceIn(0, 1000)
        httpClient.get("$ep/exec_takemisc.cgi?com=assignafframe&point=${pX}x${pY}")
        _cameraState.value = _cameraState.value.copy(focusX = x, focusY = y)
        Logger.d(TAG) { "Touch AF at ($pX, $pY)" }
    }

    /**
     * Gets a camera property value via OPC get_camprop.cgi.
     * Response format: `<?xml version="1.0"?><get><value>VALUE</value></get>`
     */
    private suspend fun getCamProp(ep: String, propName: String): String? {
        return try {
            val response = httpClient.get("$ep/get_camprop.cgi?com=get&propname=$propName")
            parseXmlValue(response.bodyAsText())
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to read $propName: ${e.message}" }
            null
        }
    }

    /**
     * Sets a camera property value via OPC set_camprop.cgi.
     * Sends XML body: `<?xml version="1.0"?><set><value>VALUE</value></set>`
     */
    private suspend fun setCamProp(ep: String, propName: String, value: String) {
        val xmlBody = "<?xml version=\"1.0\"?><set><value>$value</value></set>"
        httpClient.post("$ep/set_camprop.cgi?com=set&propname=$propName") {
            contentType(ContentType.Text.Xml)
            setBody(xmlBody)
        }
    }

    /**
     * Extracts the value from an OM System OPC XML response.
     * Looks for `<value>...</value>` tags.
     */
    private fun parseXmlValue(xml: String): String? {
        val start = xml.indexOf("<value>")
        val end = xml.indexOf("</value>")
        if (start >= 0 && end > start) {
            return xml.substring(start + "<value>".length, end).trim()
        }
        return null
    }
}
