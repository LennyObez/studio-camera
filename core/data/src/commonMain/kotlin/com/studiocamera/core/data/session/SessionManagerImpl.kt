package com.studiocamera.core.data.session

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.DeviceHealth
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.model.SessionEvent
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.network.ApiEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SessionManagerImpl(
    private val httpClient: HttpClient,
    private val connectionStateManager: ConnectionStateManager,
    private val deviceStorage: DeviceStorageRepository
) : SessionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private var currentDevice: PairedDevice? = null
    private var capabilities: DeviceCapabilities? = null
    private var accessToken: String? = null
    private var wsSession: WebSocketSession? = null
    private var wsJob: Job? = null
    private var keepaliveJob: Job? = null
    private var reconnectAttempts = 0

    companion object {
        private const val MAX_RECONNECT_RETRIES = 10
        private const val KEEPALIVE_ACTIVE_MS = 5_000L
        private const val KEEPALIVE_IDLE_MS = 15_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val TAG = "SessionManager"
    }

    override suspend fun connect(device: PairedDevice) {
        Logger.i(TAG) { "Connecting to ${device.deviceName}" }
        currentDevice = device
        reconnectAttempts = 0
        updateState(ConnectionState.Connecting)

        try {
            // Retrieve stored session
            val session = deviceStorage.getSessionInfo(device.deviceId)
            if (session != null) {
                accessToken = session.accessToken
            }

            // Verify fingerprint (TOFU)
            val trustedFp = deviceStorage.getTrustedFingerprint(device.deviceId)
            if (trustedFp != null && trustedFp != device.fingerprint && device.fingerprint.isNotEmpty()) {
                updateState(ConnectionState.Failed)
                _events.emit(SessionEvent.Error(SessionError.FingerprintMismatch()))
                return
            }

            // Health check
            updateState(ConnectionState.Authenticating)
            httpClient.get("${device.endpoint}${ApiEndpoints.HEALTH}")

            // Fetch capabilities
            updateState(ConnectionState.Binding)
            if (accessToken != null) {
                try {
                    val response = httpClient.get("${device.endpoint}${ApiEndpoints.INFO}") {
                        bearerAuth(accessToken!!)
                    }
                    capabilities = response.body<DeviceCapabilities>()
                } catch (e: Exception) {
                    Logger.w(TAG) { "Failed to fetch capabilities: ${e.message}" }
                    capabilities = DeviceCapabilities()
                }
            }

            // Open WebSocket
            openWebSocket(device.endpoint)

            // Success
            updateState(ConnectionState.Connected)
            connectionStateManager.setConnectedDevice(device.copy(capabilities = capabilities ?: DeviceCapabilities()))
            connectionStateManager.updateState(ConnectionState.Connected)
            startKeepalive()
            Logger.i(TAG) { "Connected to ${device.deviceName}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Connection failed" }
            updateState(ConnectionState.Failed)
            _events.emit(SessionEvent.Error(SessionError.DeviceUnreachable()))
        }
    }

    override suspend fun disconnect() {
        Logger.i(TAG) { "Disconnecting" }
        keepaliveJob?.cancel()
        wsJob?.cancel()
        wsSession?.close()
        wsSession = null
        currentDevice = null
        capabilities = null
        accessToken = null
        reconnectAttempts = 0
        updateState(ConnectionState.Disconnected)
        connectionStateManager.disconnect()
    }

    override suspend fun reconnect() {
        val device = currentDevice ?: return
        Logger.i(TAG) { "Reconnecting (attempt ${reconnectAttempts + 1})" }

        if (reconnectAttempts >= MAX_RECONNECT_RETRIES) {
            updateState(ConnectionState.Failed)
            _events.emit(SessionEvent.Error(SessionError.MaxRetriesExceeded()))
            return
        }

        updateState(ConnectionState.Reconnecting)
        connectionStateManager.updateState(ConnectionState.Reconnecting)

        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s cap
        val backoffMs = minOf(1000L * (1L shl reconnectAttempts), MAX_BACKOFF_MS)
        reconnectAttempts++
        delay(backoffMs)

        try {
            connect(device)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG) { "Reconnect attempt $reconnectAttempts failed: ${e.message}" }
            scope.launch { reconnect() }
        }
    }

    override fun isConnected(): Boolean = _state.value == ConnectionState.Connected

    override fun currentCapabilities(): DeviceCapabilities? = capabilities

    private suspend fun openWebSocket(endpoint: String) {
        val wsUrl = endpoint
            .replace("https://", "wss://")
            .replace("http://", "ws://") + ApiEndpoints.WEBSOCKET

        wsJob?.cancel()
        wsJob = scope.launch {
            try {
                wsSession = httpClient.webSocketSession(wsUrl) {
                    if (accessToken != null) {
                        bearerAuth(accessToken!!)
                    }
                }
                Logger.d(TAG) { "WebSocket connected" }

                for (frame in wsSession!!.incoming) {
                    when (frame) {
                        is Frame.Text -> handleWsMessage(frame.readText())
                        is Frame.Close -> {
                            Logger.d(TAG) { "WebSocket closed by server" }
                            if (_state.value == ConnectionState.Connected) {
                                scope.launch { reconnect() }
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "WebSocket error: ${e.message}" }
                if (_state.value == ConnectionState.Connected) {
                    scope.launch { reconnect() }
                }
            }
        }
    }

    private suspend fun handleWsMessage(text: String) {
        try {
            val obj = json.decodeFromString<JsonObject>(text)
            val type = obj["type"]?.jsonPrimitive?.content ?: return

            when (type) {
                "camera.state" -> {
                    val state = json.decodeFromString<CameraState>(
                        obj["data"].toString()
                    )
                    _events.emit(SessionEvent.CameraStateUpdate(state))
                }
                "device.health" -> {
                    val health = json.decodeFromString<DeviceHealth>(
                        obj["data"].toString()
                    )
                    _events.emit(SessionEvent.DeviceHealthUpdate(health))
                }
                "media.added" -> {
                    val mediaId = obj["data"]?.jsonPrimitive?.content ?: return
                    _events.emit(SessionEvent.MediaAdded(mediaId))
                }
                else -> Logger.d(TAG) { "Unknown WS event type: $type" }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to parse WS message: ${e.message}" }
        }
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            var missedPongs = 0
            var interval = KEEPALIVE_ACTIVE_MS

            while (isActive && _state.value == ConnectionState.Connected) {
                delay(interval)
                try {
                    val session = wsSession
                    if (session != null) {
                        session.send(Frame.Ping(byteArrayOf()))
                        missedPongs = 0
                        // Adapt interval: slow down after stable connection
                        if (interval == KEEPALIVE_ACTIVE_MS) {
                            interval = KEEPALIVE_IDLE_MS
                        }
                    }
                } catch (e: Exception) {
                    missedPongs++
                    Logger.w(TAG) { "Keepalive ping failed ($missedPongs missed)" }
                    if (missedPongs >= 3) {
                        Logger.w(TAG) { "3 missed pongs - triggering reconnect" }
                        reconnect()
                        break
                    }
                }
            }
        }
    }

    private suspend fun updateState(newState: ConnectionState) {
        _state.value = newState
        _events.emit(SessionEvent.StateChanged(newState))
    }
}
