package com.studiocamera.feature.mock.data

import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.DeviceHealth
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionEvent
import com.studiocamera.core.domain.session.SessionManager
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

class MockSessionManager : SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private var telemetryJob: Job? = null

    private var batteryPercent = 100
    private var temperature = 35f
    private var storageUsed = 0L
    private val storageTotalBytes = 64L * 1024 * 1024 * 1024 // 64 GB

    private val mockCapabilities = DeviceCapabilities(
        firmwareVersion = "1.0.0-mock",
        supportsWebRTC = false,
        supportsRTSP = false,
        supportsMJPEG = true,
        supportsManualExposure = true,
        supportsManualFocus = true,
        supportsMediaDelete = true,
        supportsMdns = true
    )

    override suspend fun connect(device: PairedDevice) {
        _state.value = ConnectionState.Connecting
        _events.emit(SessionEvent.StateChanged(ConnectionState.Connecting))
        delay(500)

        _state.value = ConnectionState.Authenticating
        _events.emit(SessionEvent.StateChanged(ConnectionState.Authenticating))
        delay(300)

        _state.value = ConnectionState.Binding
        _events.emit(SessionEvent.StateChanged(ConnectionState.Binding))
        delay(400)

        _state.value = ConnectionState.Connected
        _events.emit(SessionEvent.StateChanged(ConnectionState.Connected))

        startMockTelemetry()
    }

    override suspend fun disconnect() {
        telemetryJob?.cancel()
        telemetryJob = null
        _state.value = ConnectionState.Disconnected
        _events.emit(SessionEvent.StateChanged(ConnectionState.Disconnected))
    }

    override suspend fun reconnect() {
        _state.value = ConnectionState.Reconnecting
        _events.emit(SessionEvent.StateChanged(ConnectionState.Reconnecting))
        delay(1000)
        _state.value = ConnectionState.Connected
        _events.emit(SessionEvent.StateChanged(ConnectionState.Connected))

        startMockTelemetry()
    }

    override fun isConnected(): Boolean = _state.value == ConnectionState.Connected

    override fun currentCapabilities(): DeviceCapabilities = mockCapabilities

    override fun currentEndpoint(): String = "mock://localhost"

    override fun currentAccessToken(): String = "mock-token"

    override fun onForeground() {
        // No-op for mock
    }

    override fun onBackground() {
        // No-op for mock
    }

    private fun startMockTelemetry() {
        // Cancel any existing telemetry before starting a new one
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive && _state.value == ConnectionState.Connected) {
                delay(30_000) // Every 30s

                // Battery drain ~1%/30s
                batteryPercent = (batteryPercent - 1).coerceAtLeast(0)

                // Temperature fluctuation 35-45C
                temperature = (temperature + (-0.5f..1.5f).random()).coerceIn(35f, 45f)

                val health = DeviceHealth(
                    batteryPercent = batteryPercent,
                    isCharging = false,
                    storageUsedBytes = storageUsed,
                    storageTotalBytes = storageTotalBytes,
                    temperatureCelsius = temperature
                )
                _events.emit(SessionEvent.DeviceHealthUpdate(health))
            }
        }
    }

    private fun ClosedFloatingPointRange<Float>.random(): Float {
        return start + (kotlin.random.Random.nextFloat() * (endInclusive - start))
    }
}
