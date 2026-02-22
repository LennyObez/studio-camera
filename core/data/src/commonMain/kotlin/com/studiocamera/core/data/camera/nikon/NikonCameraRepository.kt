package com.studiocamera.core.data.camera.nikon

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.ptpip.PtpIpCodec
import com.studiocamera.core.data.camera.ptpip.PtpIpPacketType
import com.studiocamera.core.data.camera.ptpip.PtpIpSession
import com.studiocamera.core.data.camera.ptpip.PtpOpCode
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.CameraRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Nikon camera control via PTP/IP (CIPA DC-005) on port 15740.
 *
 * Nikon cameras use standard PTP/IP with vendor-specific operation codes:
 * - 0x90C0: AfDrive (auto-focus trigger)
 * - 0x9201: StartLiveView
 * - 0x9202: StopLiveView
 * - 0x9203: GetLiveViewImage
 * - 0x9204: MfDrive (manual focus drive)
 * - 0xD0A4: MovieRecordTarget (device property for video recording)
 */
class NikonCameraRepository(
    private val endpoint: () -> String
) : CameraRepository {

    companion object {
        private const val TAG = "NikonCamera"
        private const val PTP_PORT = 15740

        // Nikon vendor operation codes
        private val OP_AF_DRIVE: UShort = 0x90C0u
        private val OP_START_LIVE_VIEW: UShort = 0x9201u
        private val OP_STOP_LIVE_VIEW: UShort = 0x9202u
        private val OP_GET_LIVE_VIEW_IMAGE: UShort = 0x9203u
        private val OP_MF_DRIVE: UShort = 0x9204u

        // Nikon device property codes
        private const val PROP_MOVIE_REC_TARGET = 0xD0A4
        private const val PROP_ISO = 0x500F
        private const val PROP_SHUTTER_SPEED = 0x500D
        private const val PROP_APERTURE = 0x5007
        private const val PROP_EV_COMP = 0x5010
        private const val PROP_WHITE_BALANCE = 0x5005
        private const val PROP_EXPOSURE_MODE = 0x500E
    }

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    @Volatile
    private var session: PtpIpSession? = null

    private fun extractHost(): String {
        val ep = endpoint()
        // Extract IP from endpoint URL or raw IP
        val hostRegex = Regex("""://([^:/]+)""")
        return hostRegex.find(ep)?.groupValues?.get(1)
            ?: Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").find(ep)?.value
            ?: ep
    }

    override suspend fun initializeSession(): ApiResult<Unit> {
        val ep = endpoint()
        if (ep.isBlank()) {
            return ApiResult.Error(SessionError.DeviceUnreachable)
        }
        return try {
            // Close any existing session before opening a new one
            session?.let {
                try { it.close() } catch (_: Exception) {}
                session = null
            }

            val host = extractHost()
            val ptpSession = PtpIpSession(host, PTP_PORT)
            ptpSession.open()
            session = ptpSession
            Logger.i(TAG) { "Nikon PTP/IP session opened to $host" }

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

        return flow {
            val s = session ?: return@flow

            try {
                // Start live view mode
                s.executeOperation(OP_START_LIVE_VIEW)
                Logger.d(TAG) { "Nikon live view started" }

                while (currentCoroutineContext().isActive) {
                    try {
                        val (code, data) = s.executeOperation(OP_GET_LIVE_VIEW_IMAGE)
                        if (code == PtpOpCode.RESPONSE_OK && data.isNotEmpty()) {
                            // Nikon live view data includes a header before the JPEG.
                            // Find JPEG start (0xFF 0xD8) and end (0xFF 0xD9) markers
                            val jpegStart = findJpegStart(data)
                            if (jpegStart >= 0) {
                                val jpegEnd = findJpegEnd(data, jpegStart)
                                val endIdx = if (jpegEnd >= 0) jpegEnd + 2 else data.size
                                emit(data.copyOfRange(jpegStart, endIdx))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.d(TAG) { "Live view frame read error: ${e.message}" }
                    }
                }
            } finally {
                try { s.executeOperation(OP_STOP_LIVE_VIEW) } catch (_: Exception) {}
                Logger.d(TAG) { "Nikon live view stopped" }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun capturePhoto(): ApiResult<String> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            // AF drive first, then initiate capture
            try { s.executeOperation(OP_AF_DRIVE) } catch (_: Exception) {}
            val (code, _) = s.executeOperation(PtpOpCode.INITIATE_CAPTURE, intArrayOf(0, 0))
            if (code == PtpOpCode.RESPONSE_OK) {
                Logger.d(TAG) { "Photo captured" }
                ApiResult.Success("captured")
            } else {
                ApiResult.Error(SessionError.Unknown(IllegalStateException("Capture failed: 0x${code.toString(16)}")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "capturePhoto failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
    }

    override suspend fun startRecording(): ApiResult<Unit> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            // Set movie record target to start recording
            val data = ByteArray(4)
            PtpIpCodec.writeLeInt32(data, 0, 1) // 1 = start recording
            s.executeOperationWithData(
                PtpOpCode.SET_DEVICE_PROP_VALUE,
                intArrayOf(PROP_MOVIE_REC_TARGET),
                data
            )
            _cameraState.value = _cameraState.value.copy(isRecording = true)
            Logger.d(TAG) { "Recording started" }
            ApiResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "startRecording failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
    }

    override suspend fun stopRecording(): ApiResult<String> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            val data = ByteArray(4)
            PtpIpCodec.writeLeInt32(data, 0, 0) // 0 = stop recording
            s.executeOperationWithData(
                PtpOpCode.SET_DEVICE_PROP_VALUE,
                intArrayOf(PROP_MOVIE_REC_TARGET),
                data
            )
            _cameraState.value = _cameraState.value.copy(isRecording = false, recordingDurationMs = 0L)
            Logger.d(TAG) { "Recording stopped" }
            ApiResult.Success("recorded")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "stopRecording failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
    }

    override suspend fun setShootMode(mode: String): ApiResult<Unit> {
        // Nikon exposure mode is set via mode dial — update local state only
        _cameraState.value = _cameraState.value.copy(shootMode = mode)
        return ApiResult.Success(Unit)
    }

    override suspend fun getSettings(): ApiResult<CameraState> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            var state = _cameraState.value

            getDevicePropInt(s, PROP_ISO)?.let { state = state.copy(iso = it) }
            getDevicePropInt(s, PROP_SHUTTER_SPEED)?.let { state = state.copy(shutterSpeed = nikonShutterToString(it)) }
            getDevicePropInt(s, PROP_APERTURE)?.let { state = state.copy(aperture = it.toFloat() / 100f) }
            getDevicePropInt(s, PROP_EV_COMP)?.let { state = state.copy(ev = it.toFloat() / 1000f) }
            getDevicePropInt(s, PROP_WHITE_BALANCE)?.let { state = state.copy(whiteBalance = nikonWbToString(it)) }
            getDevicePropInt(s, PROP_EXPOSURE_MODE)?.let { state = state.copy(exposureMode = nikonModeToString(it)) }

            _cameraState.value = state.copy(valuesReported = true)
            ApiResult.Success(state.copy(valuesReported = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getSettings failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
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
    ): ApiResult<CameraState> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            var state = _cameraState.value

            iso?.let {
                setDevicePropInt(s, PROP_ISO, it)
                state = state.copy(iso = it)
            }
            shutterSpeed?.let {
                setDevicePropInt(s, PROP_SHUTTER_SPEED, nikonStringToShutter(it))
                state = state.copy(shutterSpeed = it)
            }
            aperture?.let {
                setDevicePropInt(s, PROP_APERTURE, (it * 100).toInt())
                state = state.copy(aperture = it)
            }
            ev?.let {
                setDevicePropInt(s, PROP_EV_COMP, (it * 1000).toInt())
                state = state.copy(ev = it)
            }
            isAutoFocus?.let { state = state.copy(isAutoFocus = it) }
            flashMode?.let { state = state.copy(flashMode = it) }
            imageFormat?.let { state = state.copy(imageFormat = it) }
            photoResolution?.let { state = state.copy(photoResolution = it) }
            videoResolution?.let { state = state.copy(videoResolution = it) }
            videoFps?.let { state = state.copy(videoFps = it) }
            hdrEnabled?.let { state = state.copy(hdrEnabled = it) }
            whiteBalance?.let {
                setDevicePropInt(s, PROP_WHITE_BALANCE, nikonStringToWb(it))
                state = state.copy(whiteBalance = it)
            }
            exposureMode?.let {
                // Nikon exposure mode is typically set via physical dial;
                // attempt to set via PTP — some bodies allow it in certain modes
                state = state.copy(exposureMode = it)
            }

            _cameraState.value = state
            ApiResult.Success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "updateSettings failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
    }

    override suspend fun tapToFocus(x: Float, y: Float): ApiResult<Unit> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            s.executeOperation(OP_AF_DRIVE)
            _cameraState.value = _cameraState.value.copy(focusX = x, focusY = y)
            Logger.d(TAG) { "AF drive triggered" }
            ApiResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "tapToFocus failed" }
            ApiResult.Error(SessionError.Unknown(e))
        }
    }

    // --- PTP property helpers ---

    private fun getDevicePropInt(s: PtpIpSession, propCode: Int): Int? {
        return try {
            val (code, data) = s.executeOperation(
                PtpOpCode.GET_DEVICE_PROP_VALUE,
                intArrayOf(propCode)
            )
            if (code == PtpOpCode.RESPONSE_OK && data.size >= 4) {
                PtpIpCodec.readLeInt32(data, 0)
            } else if (code == PtpOpCode.RESPONSE_OK && data.size >= 2) {
                PtpIpCodec.readLeUInt16(data, 0).toInt()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun setDevicePropInt(s: PtpIpSession, propCode: Int, value: Int) {
        val data = ByteArray(4)
        PtpIpCodec.writeLeInt32(data, 0, value)
        s.executeOperationWithData(
            PtpOpCode.SET_DEVICE_PROP_VALUE,
            intArrayOf(propCode),
            data
        )
    }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun findJpegEnd(data: ByteArray, startFrom: Int): Int {
        for (i in startFrom until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun nikonShutterToString(value: Int): String = when {
        value >= 65536 -> "Bulb"
        value > 10 -> "1/${value / 10}"
        else -> "${value}s"
    }

    private fun nikonStringToShutter(str: String): Int = when {
        str.equals("Bulb", ignoreCase = true) -> 65536
        str.startsWith("1/") -> (str.removePrefix("1/").toIntOrNull() ?: 60) * 10
        str.endsWith("s") -> str.removeSuffix("s").toIntOrNull() ?: 1
        else -> str.toIntOrNull() ?: 600
    }

    private fun nikonWbToString(value: Int): String = when (value) {
        2 -> "Auto"
        4 -> "Daylight"
        5 -> "Fluorescent"
        6 -> "Tungsten"
        7 -> "Flash"
        0x8010 -> "Cloudy"
        0x8011 -> "Shade"
        0x8012 -> "Color temp"
        else -> "Auto"
    }

    private fun nikonModeToString(value: Int): String = when (value) {
        1 -> "M"
        2 -> "P"
        3 -> "A"
        4 -> "S"
        else -> "P"
    }

    private fun nikonStringToWb(wb: String): Int = when (wb.lowercase()) {
        "auto" -> 2
        "daylight" -> 4
        "fluorescent" -> 5
        "tungsten" -> 6
        "flash" -> 7
        "cloudy" -> 0x8010
        "shade" -> 0x8011
        "color temp" -> 0x8012
        else -> 2
    }
}
