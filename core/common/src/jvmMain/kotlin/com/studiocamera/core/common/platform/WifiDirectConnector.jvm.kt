package com.studiocamera.core.common.platform

actual class WifiDirectConnector {
    var connectResult: WifiDirectResult = WifiDirectResult.Connected("192.168.1.1")
    var disconnectCalled: Boolean = false
        private set
    actual var onNetworkLost: (() -> Unit)? = null

    actual suspend fun connect(ssid: String, password: String): WifiDirectResult {
        return connectResult
    }

    actual suspend fun disconnect() {
        disconnectCalled = true
    }
}
