package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val accessToken: String,
    val refreshToken: String,
    val connectedAt: Long
)

sealed class SessionEvent {
    data class StateChanged(val state: ConnectionState) : SessionEvent()
    data class CameraStateUpdate(val state: CameraState) : SessionEvent()
    data class DeviceHealthUpdate(val health: DeviceHealth) : SessionEvent()
    data class MediaAdded(val mediaId: String) : SessionEvent()
    data class Error(val error: SessionError) : SessionEvent()
}
