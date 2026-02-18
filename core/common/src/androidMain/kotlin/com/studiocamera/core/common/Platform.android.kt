package com.studiocamera.core.common

import java.net.NetworkInterface

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getCurrentLinkAddress(): String? {
    return try {
        val addresses = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            .map { it.hostAddress }
            .toList()

        // Find the address in the Wi-Fi Direct subnet (typically 192.168.x.x)
        addresses.firstOrNull { address ->
            address?.startsWith("192.168.") == true
        } ?: addresses.firstOrNull()
    } catch (e: Exception) {
        null
    }
}
