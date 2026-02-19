package com.studiocamera.core.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.AF_INET
import platform.posix.ifaddrs
import platform.posix.getifaddrs
import platform.posix.freeifaddrs
import platform.posix.sockaddr_in
import platform.posix.inet_ntoa

actual fun currentEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * Returns the IPv4 address of the en0 (Wi-Fi) interface, preferring 192.168.x.x addresses.
 * Uses POSIX [getifaddrs] to enumerate network interfaces.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun getCurrentLinkAddress(): String? {
    memScoped {
        val ifaddrsPtr = alloc<kotlinx.cinterop.CPointerVar<ifaddrs>>()
        if (getifaddrs(ifaddrsPtr.ptr) != 0) return null

        var ifaddr = ifaddrsPtr.value
        var bestAddress: String? = null

        try {
            while (ifaddr != null) {
                val ifa = ifaddr!!.pointed
                val name = ifa.ifa_name?.toKString()
                val family = ifa.ifa_addr?.pointed?.sa_family?.toInt()

                if (name == "en0" && family == AF_INET) {
                    val sockAddr = ifa.ifa_addr!!.reinterpret<sockaddr_in>().pointed
                    val ipStr = inet_ntoa(sockAddr.sin_addr.readValue())?.toKString()
                    if (ipStr != null) {
                        // Prefer 192.168.x.x addresses (camera network range)
                        if (ipStr.startsWith("192.168.")) {
                            return ipStr
                        }
                        if (bestAddress == null) {
                            bestAddress = ipStr
                        }
                    }
                }
                ifaddr = ifa.ifa_next
            }
        } finally {
            freeifaddrs(ifaddrsPtr.value)
        }

        return bestAddress
    }
}
