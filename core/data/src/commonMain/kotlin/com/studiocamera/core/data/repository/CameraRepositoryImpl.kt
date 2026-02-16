package com.studiocamera.core.data.repository

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.network.ApiEndpoints
import com.studiocamera.core.network.safeApiCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

class CameraRepositoryImpl(
    private val httpClient: HttpClient,
    private val endpoint: () -> String,
    private val accessToken: () -> String?
) : CameraRepository {

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    override suspend fun capturePhoto(): ApiResult<String> = safeApiCall {
        val response = httpClient.post("${endpoint()}${ApiEndpoints.CAMERA_CAPTURE}") {
            accessToken()?.let { bearerAuth(it) }
        }
        val result = response.body<CaptureResponse>()
        Logger.d("Camera") { "Photo captured: ${result.mediaId}" }
        result.mediaId
    }

    override suspend fun startRecording(): ApiResult<Unit> = safeApiCall {
        httpClient.post("${endpoint()}${ApiEndpoints.CAMERA_RECORD_START}") {
            accessToken()?.let { bearerAuth(it) }
        }
        _cameraState.value = _cameraState.value.copy(isRecording = true)
        Logger.d("Camera") { "Recording started" }
    }

    override suspend fun stopRecording(): ApiResult<String> = safeApiCall {
        val response = httpClient.post("${endpoint()}${ApiEndpoints.CAMERA_RECORD_STOP}") {
            accessToken()?.let { bearerAuth(it) }
        }
        val result = response.body<CaptureResponse>()
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
        Logger.d("Camera") { "Recording stopped: ${result.mediaId}" }
        result.mediaId
    }

    override suspend fun getSettings(): ApiResult<CameraState> = safeApiCall {
        val response = httpClient.get("${endpoint()}${ApiEndpoints.CAMERA_SETTINGS}") {
            accessToken()?.let { bearerAuth(it) }
        }
        val state = response.body<CameraState>()
        _cameraState.value = state
        state
    }

    override suspend fun updateSettings(
        iso: Int?,
        shutterSpeed: String?,
        aperture: Float?,
        ev: Float?,
        isAutoFocus: Boolean?
    ): ApiResult<CameraState> = safeApiCall {
        val update = SettingsUpdate(
            iso = iso,
            shutterSpeed = shutterSpeed,
            aperture = aperture,
            ev = ev,
            isAutoFocus = isAutoFocus
        )
        val response = httpClient.patch("${endpoint()}${ApiEndpoints.CAMERA_SETTINGS}") {
            contentType(ContentType.Application.Json)
            setBody(update)
            accessToken()?.let { bearerAuth(it) }
        }
        val state = response.body<CameraState>()
        _cameraState.value = state
        state
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> = safeApiCall {
        httpClient.post("${endpoint()}${ApiEndpoints.CAMERA_FOCUS}") {
            contentType(ContentType.Application.Json)
            setBody(FocusRequest(x, y))
            accessToken()?.let { bearerAuth(it) }
        }
        Logger.d("Camera") { "Tap-to-focus at ($x, $y)" }
    }

    fun updateFromWebSocket(state: CameraState) {
        _cameraState.value = state
    }
}

@Serializable
private data class CaptureResponse(val mediaId: String)

@Serializable
private data class SettingsUpdate(
    val iso: Int? = null,
    val shutterSpeed: String? = null,
    val aperture: Float? = null,
    val ev: Float? = null,
    val isAutoFocus: Boolean? = null
)

@Serializable
private data class FocusRequest(val x: Float, val y: Float)
