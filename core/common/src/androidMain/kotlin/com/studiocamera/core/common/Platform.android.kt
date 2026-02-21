package com.studiocamera.core.common

import java.net.NetworkInterface

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getCurrentLinkAddress(): String? {
    return try {
        // Prefer Wi-Fi interfaces (wlan0, p2p-*) over mobile data or VPN
        val wifiInterfaceNames = setOf("wlan0", "wlan1")
        val wifiAddresses = NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { iface ->
                val name = iface.name
                name in wifiInterfaceNames || name.startsWith("p2p-")
            }
            .flatMap { it.inetAddresses.asSequence() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            .map { it.hostAddress }
            .toList()

        // Prefer 192.168.x.x (Wi-Fi Direct subnet)
        wifiAddresses.firstOrNull { it?.startsWith("192.168.") == true }
            ?: wifiAddresses.firstOrNull()
            ?: run {
                // Fallback: any non-loopback IPv4 in 192.168.x.x
                NetworkInterface.getNetworkInterfaces().asSequence()
                    .flatMap { it.inetAddresses.asSequence() }
                    .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    .map { it.hostAddress }
                    .firstOrNull { it?.startsWith("192.168.") == true }
            }
    } catch (e: Exception) {
        null
    }
}
