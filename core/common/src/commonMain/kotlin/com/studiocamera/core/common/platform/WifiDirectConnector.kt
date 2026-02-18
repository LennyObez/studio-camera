package com.studiocamera.core.common.platform

sealed class WifiDirectResult {
    data class Connected(val gatewayIp: String) : WifiDirectResult()
    data class Failed(val reason: String) : WifiDirectResult()
    data object UserCancelled : WifiDirectResult()
    data object OpenWifiSettings : WifiDirectResult()
    data object NeedsNearbyWifiPermission : WifiDirectResult()
}

expect class WifiDirectConnector {
    suspend fun connect(ssid: String, password: String): WifiDirectResult
    suspend fun disconnect()
    var onNetworkLost: (() -> Unit)?
}
