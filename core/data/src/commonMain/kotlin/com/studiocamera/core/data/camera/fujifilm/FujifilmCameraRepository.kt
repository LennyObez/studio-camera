package com.studiocamera.core.data.camera.fujifilm

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.ptpip.PtpIpCodec
import com.studiocamera.core.data.camera.ptpip.PtpIpSession
import com.studiocamera.core.data.camera.ptpip.PtpOpCode
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.network.TcpSocket
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
 * Fujifilm camera control via PTP/IP variant.
 *
 * Fujifilm uses non-standard ports:
 * - Port 55740: Command connection
 * - Port 55741: Event connection
 * - Port 55742: Live view stream (dedicated TCP, JPEG frames length-prefixed)
 *
 * The PTP/IP handshake and operations follow the standard CIPA DC-005 format,
 * but with Fujifilm vendor-specific property codes.
 */
class FujifilmCameraRepository(
    private val endpoint: () -> String
) : CameraRepository {

    companion object {
        private const val TAG = "FujifilmCamera"
        private const val COMMAND_PORT = 55740
        private const val EVENT_PORT = 55741
        private const val LIVE_VIEW_PORT = 55742

        // Fujifilm vendor property codes
        private const val PROP_ISO: Int = 0xD02A
        private const val PROP_SHUTTER_SPEED: Int = 0xD02C
        private const val PROP_APERTURE: Int = 0xD02B
        private const val PROP_EV_COMP: Int = 0xD02E
        private const val PROP_WHITE_BALANCE: Int = 0xD02D
        private const val PROP_FILM_SIMULATION: Int = 0xD001
        private const val PROP_MOVIE_MODE: Int = 0xD0A4

        // Use standard PtpOpCode.INITIATE_CAPTURE (0x100E) for capture
    }

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    @Volatile
    private var session: PtpIpSession? = null

    private fun extractHost(): String {
        val ep = endpoint()
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
            val ptpSession = PtpIpSession(host, COMMAND_PORT, EVENT_PORT)
            ptpSession.open()
            session = ptpSession
            Logger.i(TAG) { "Fujifilm PTP/IP session opened to $host" }

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
            val host = extractHost()
            var lvSocket: TcpSocket? = null
            try {
                lvSocket = TcpSocket(host, LIVE_VIEW_PORT, 15_000)
                Logger.d(TAG) { "Fujifilm live view connected on port $LIVE_VIEW_PORT" }

                val headerBuf = ByteArray(4)
                while (currentCoroutineContext().isActive) {
                    // Read 4-byte length prefix (LE)
                    readExact(lvSocket, headerBuf, 4)
                    val frameLen = PtpIpCodec.readLeInt32(headerBuf, 0)

                    if (frameLen <= 0 || frameLen > 2_000_000) continue

                    val frameData = ByteArray(frameLen)
                    readExact(lvSocket, frameData, frameLen)

                    // Find JPEG start marker — Fujifilm may include a small header
                    val jpegStart = findJpegStart(frameData)
                    if (jpegStart >= 0) {
                        emit(frameData.copyOfRange(jpegStart, frameData.size))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Live view stream ended: ${e.message}" }
            } finally {
                lvSocket?.close()
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun capturePhoto(): ApiResult<String> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
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
            setDevicePropInt(s, PROP_MOVIE_MODE, 1)
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
            setDevicePropInt(s, PROP_MOVIE_MODE, 0)
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
        // Fujifilm mode is set via physical dial — update local state only
        _cameraState.value = _cameraState.value.copy(shootMode = mode)
        return ApiResult.Success(Unit)
    }

    override suspend fun getSettings(): ApiResult<CameraState> {
        val s = session ?: return ApiResult.Error(SessionError.DeviceUnreachable)
        return try {
            var state = _cameraState.value

            getDevicePropInt(s, PROP_ISO)?.let { state = state.copy(iso = fujiIsoToStandard(it)) }
            getDevicePropInt(s, PROP_SHUTTER_SPEED)?.let { state = state.copy(shutterSpeed = fujiShutterToString(it)) }
            getDevicePropInt(s, PROP_APERTURE)?.let { state = state.copy(aperture = it.toFloat() / 100f) }
            getDevicePropInt(s, PROP_EV_COMP)?.let { state = state.copy(ev = fujiEvToFloat(it)) }
            getDevicePropInt(s, PROP_WHITE_BALANCE)?.let { state = state.copy(whiteBalance = fujiWbToString(it)) }

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
                setDevicePropInt(s, PROP_ISO, standardIsoToFuji(it))
                state = state.copy(iso = it)
            }
            shutterSpeed?.let {
                setDevicePropInt(s, PROP_SHUTTER_SPEED, fujiStringToShutter(it))
                state = state.copy(shutterSpeed = it)
            }
            aperture?.let {
                setDevicePropInt(s, PROP_APERTURE, (it * 100).toInt())
                state = state.copy(aperture = it)
            }
            ev?.let {
                setDevicePropInt(s, PROP_EV_COMP, fujiFloatToEv(it))
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
                setDevicePropInt(s, PROP_WHITE_BALANCE, fujiStringToWb(it))
                state = state.copy(whiteBalance = it)
            }
            exposureMode?.let {
                // Fujifilm exposure mode is typically set via physical dial
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
        _cameraState.value = _cameraState.value.copy(focusX = x, focusY = y)
        Logger.d(TAG) { "Focus point set to ($x, $y)" }
        return ApiResult.Success(Unit)
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

    private fun readExact(socket: TcpSocket, dest: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val remaining = length - offset
            val temp = ByteArray(minOf(remaining, 65536))
            val n = socket.receive(temp)
            if (n <= 0) error("Connection closed during live view read")
            temp.copyInto(dest, offset, 0, n)
            offset += n
        }
    }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                return i
            }
        }
        return -1
    }

    // --- Fujifilm value conversion helpers ---

    private fun fujiIsoToStandard(value: Int): Int = when {
        value <= 0 -> 200
        else -> value
    }

    private fun standardIsoToFuji(iso: Int): Int = iso

    private fun fujiShutterToString(value: Int): String {
        if (value <= 0) return "Auto"
        // Fujifilm encodes shutter as numerator in high 16 bits, denominator in low 16 bits
        val num = (value shr 16) and 0xFFFF
        val den = value and 0xFFFF
        return when {
            den == 0 -> "Bulb"
            num == 1 -> "1/$den"
            num > 0 && den == 1 -> "${num}s"
            else -> "$num/$den"
        }
    }

    private fun fujiStringToShutter(str: String): Int = when {
        str.equals("Bulb", ignoreCase = true) -> 0
        str.equals("Auto", ignoreCase = true) -> -1
        str.startsWith("1/") -> {
            val den = str.removePrefix("1/").toIntOrNull() ?: 60
            (1 shl 16) or den
        }
        str.endsWith("s") -> {
            val num = str.removeSuffix("s").toIntOrNull() ?: 1
            (num shl 16) or 1
        }
        else -> (1 shl 16) or 60
    }

    private fun fujiEvToFloat(value: Int): Float {
        // Fujifilm EV is encoded in 1/3 stop increments
        return value.toFloat() / 3f
    }

    private fun fujiFloatToEv(ev: Float): Int {
        return (ev * 3f).toInt()
    }

    private fun fujiWbToString(value: Int): String = when (value) {
        2 -> "Auto"
        4 -> "Daylight"
        6 -> "Shade"
        0x8001 -> "Fluorescent 1"
        0x8002 -> "Fluorescent 2"
        0x8003 -> "Fluorescent 3"
        0x8006 -> "Tungsten"
        0x800A -> "Underwater"
        0x800B -> "Color temp"
        else -> "Auto"
    }

    private fun fujiStringToWb(wb: String): Int = when (wb.lowercase()) {
        "auto" -> 2
        "daylight" -> 4
        "shade" -> 6
        "fluorescent 1" -> 0x8001
        "fluorescent 2" -> 0x8002
        "fluorescent 3" -> 0x8003
        "tungsten" -> 0x8006
        "underwater" -> 0x800A
        "color temp" -> 0x800B
        else -> 2
    }
}
