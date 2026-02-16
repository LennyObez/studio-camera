package com.studiocamera.core.domain.model

enum class ConnectionState {
    Disconnected,
    Connecting,
    Authenticating,
    Binding,
    Connected,
    Reconnecting,
    Failed
}
