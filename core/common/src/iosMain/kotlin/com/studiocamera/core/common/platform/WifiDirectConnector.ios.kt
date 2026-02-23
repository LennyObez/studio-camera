package com.studiocamera.core.common.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.NetworkExtension.NEHotspotConfiguration
import platform.NetworkExtension.NEHotspotConfigurationManager
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.posix.AF_INET
import platform.posix.ifaddrs
import platform.posix.getifaddrs
import platform.posix.freeifaddrs
import platform.posix.sockaddr_in
import platform.posix.inet_ntoa
import kotlin.coroutines.resume

private const val TAG = "WifiDirectConnector"

/**
 * Connects to camera Wi-Fi networks on iOS using [NEHotspotConfiguration].
 *
 * Unlike Android, iOS doesn't need network binding — there's no multi-network
 * routing issue. After joining the camera's Wi-Fi, all traffic routes through it.
 * [CameraNetworkBinding.boundNetwork] stays null on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
actual class WifiDirectConnector {

    actual var onNetworkLost: (() -> Unit)? = null

    private var currentSsid: String? = null
    private var pathMonitor: Any? = null

    actual suspend fun connect(ssid: String, password: String): WifiDirectResult =
        suspendCancellableCoroutine { continuation ->
            Logger.d(TAG) { "Connecting to SSID: $ssid" }

            val config = NEHotspotConfiguration(sSID = ssid, passphrase = password, isWEP = false)
            config.joinOnce = true

            NEHotspotConfigurationManager.sharedManager.applyConfiguration(config) { error ->
                if (error != null) {
                    // Error code 13 = "already associated" — treat as success
                    if (error.code == 13L) {
                        Logger.d(TAG) { "Already connected to $ssid" }
                    } else {
                        Logger.e(TAG) { "Failed to join $ssid: ${error.localizedDescription}" }
                        if (continuation.isActive) {
                            continuation.resume(
                                WifiDirectResult.Failed(
                                    error.localizedDescription ?: "Failed to join Wi-Fi"
                                )
                            )
                        }
                        return@applyConfiguration
                    }
                }

                currentSsid = ssid
                startNetworkMonitor()

                // Derive gateway IP from en0 interface address
                val gatewayIp = deriveGatewayIp() ?: "192.168.1.1"
                Logger.i(TAG) { "Connected to $ssid — gateway $gatewayIp" }

                if (continuation.isActive) {
                    continuation.resume(WifiDirectResult.Connected(gatewayIp))
                }
            }

            continuation.invokeOnCancellation {
                stopNetworkMonitor()
                currentSsid?.let {
                    NEHotspotConfigurationManager.sharedManager.removeConfigurationForSSID(it)
                }
                currentSsid = null
            }
        }

    actual suspend fun disconnect() {
        stopNetworkMonitor()
        currentSsid?.let { ssid ->
            Logger.d(TAG) { "Disconnecting from $ssid" }
            NEHotspotConfigurationManager.sharedManager.removeConfigurationForSSID(ssid)
        }
        currentSsid = null
    }

    /**
     * Monitor Wi-Fi path changes via NWPathMonitor. If Wi-Fi is lost, fire [onNetworkLost].
     */
    private fun startNetworkMonitor() {
        stopNetworkMonitor()
        val monitor = nw_path_monitor_create()
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        nw_path_monitor_set_queue(monitor, queue)

        nw_path_monitor_set_update_handler(monitor) { path ->
            val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            val hasWifi = nw_path_uses_interface_type(path, nw_interface_type_wifi)
            if (!satisfied || !hasWifi) {
                Logger.d(TAG) { "Network path lost Wi-Fi" }
                onNetworkLost?.invoke()
            }
        }

        nw_path_monitor_start(monitor)
        pathMonitor = monitor
    }

    private fun stopNetworkMonitor() {
        @Suppress("UNCHECKED_CAST")
        (pathMonitor)?.let {
            nw_path_monitor_cancel(it as platform.Network.nw_path_monitor_t)
        }
        pathMonitor = null
    }

    /**
     * Get the en0 (Wi-Fi) IPv4 address and derive the gateway as x.x.x.1.
     * Same heuristic as Android — camera APs use .1 as gateway.
     */
    private fun deriveGatewayIp(): String? {
        memScoped {
            val ifaddrsPtr = alloc<kotlinx.cinterop.CPointerVar<ifaddrs>>()
            if (getifaddrs(ifaddrsPtr.ptr) != 0) return null

            var ifaddr = ifaddrsPtr.value
            var result: String? = null

            try {
                while (ifaddr != null) {
                    val ifa = ifaddr!!.pointed
                    val name = ifa.ifa_name?.toKString()
                    val family = ifa.ifa_addr?.pointed?.sa_family?.toInt()

                    if (name == "en0" && family == AF_INET) {
                        val sockAddr = ifa.ifa_addr!!.reinterpret<sockaddr_in>().pointed
                        val ipStr = inet_ntoa(sockAddr.sin_addr.readValue())?.toKString()
                        if (ipStr != null) {
                            result = ipStr.substringBeforeLast('.') + ".1"
                            break
                        }
                    }
                    ifaddr = ifa.ifa_next
                }
            } finally {
                freeifaddrs(ifaddrsPtr.value)
            }

            return result
        }
    }
}
