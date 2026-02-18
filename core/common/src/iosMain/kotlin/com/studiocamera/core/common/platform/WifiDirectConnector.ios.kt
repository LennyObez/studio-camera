package com.studiocamera.core.common.platform

import co.touchlab.kermit.Logger

actual class WifiDirectConnector {
    actual var onNetworkLost: (() -> Unit)? = null

    actual suspend fun connect(ssid: String, password: String): WifiDirectResult {
        // iOS: NEHotspotConfiguration — full implementation requires macOS build
        Logger.w("WifiDirectConnector") { "iOS Wi-Fi Direct not yet implemented for SSID: $ssid" }
        return WifiDirectResult.Failed("iOS Wi-Fi Direct not yet implemented")
    }

    actual suspend fun disconnect() {
        // No-op on iOS stub
    }
}
