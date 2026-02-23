package com.studiocamera.core.common.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import co.touchlab.kermit.Logger
import com.studiocamera.core.common.CameraNetworkBinding
import java.net.Inet4Address

/**
 * Network information detected from a Wi-Fi Direct connection.
 */
private data class NetworkInfo(
    val gatewayIp: String,
    val linkAddress: String? = null
)

/**
 * Connects to camera Wi-Fi Direct networks using [WifiNetworkSpecifier] (API 29+).
 *
 * On connection, binds the process to the camera network via
 * [ConnectivityManager.bindProcessToNetwork] so that all HTTP traffic routes
 * through the camera instead of the default (internet) network. The binding
 * is restored on disconnect or network loss.
 */
actual class WifiDirectConnector(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val TAG = "WifiDirectConnector"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    actual var onNetworkLost: (() -> Unit)? = null

    actual suspend fun connect(ssid: String, password: String): WifiDirectResult =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            Logger.d(TAG) { "Connecting to SSID: $ssid" }
            disconnectSync()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)

                    val info = detectNetworkInfo(network)
                    if (info != null) {
                        Logger.i(TAG) {
                            "Network available — gateway ${info.gatewayIp}, link ${info.linkAddress ?: "none"}"
                        }
                        CameraNetworkBinding.boundNetwork = network
                        CameraNetworkBinding.onNetworkChanged?.invoke()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(WifiDirectResult.Connected(info.gatewayIp)))
                        }
                    } else {
                        Logger.w(TAG) { "Network available but failed to detect gateway" }
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(WifiDirectResult.Failed("Failed to detect network info")))
                        }
                        disconnectSync()
                    }
                }

                override fun onLost(network: Network) {
                    Logger.d(TAG) { "Network lost" }
                    clearNetworkBinding()
                    onNetworkLost?.invoke()
                }

                override fun onUnavailable() {
                    Logger.w(TAG) { "Network unavailable — user may have declined the prompt" }
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(WifiDirectResult.Failed("Network unavailable")))
                    }
                    disconnectSync()
                }
            }

            networkCallback = callback

            try {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                connectivityManager.requestNetwork(request, callback)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to request Wi-Fi Direct network" }
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(WifiDirectResult.Failed(e.message ?: "Unknown error")))
                }
            }

            continuation.invokeOnCancellation { disconnectSync() }
        }

    actual suspend fun disconnect() {
        disconnectSync()
    }

    /** Restores default network routing and clears the camera network binding. */
    private fun clearNetworkBinding() {
        CameraNetworkBinding.boundNetwork = null
        CameraNetworkBinding.onNetworkChanged?.invoke()
        connectivityManager.bindProcessToNetwork(null)
    }

    private fun disconnectSync() {
        clearNetworkBinding()
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Logger.w(TAG, e) { "Failed to unregister network callback" }
            }
            networkCallback = null
        }
    }

    /**
     * Detects gateway IP and link address from [ConnectivityManager.getLinkProperties].
     *
     * Camera Wi-Fi Direct hotspots assign the phone an IP via DHCP. The camera
     * itself is at x.x.x.1 on the same subnet (standard AP convention).
     */
    private fun detectNetworkInfo(network: Network): NetworkInfo? {
        return try {
            val linkProps = connectivityManager.getLinkProperties(network)

            val linkAddress = linkProps?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                ?.address?.hostAddress

            val gatewayIp = when {
                linkAddress != null -> linkAddress.substringBeforeLast('.') + ".1"
                else -> linkProps?.routes
                    ?.mapNotNull { it.gateway?.hostAddress }
                    ?.firstOrNull { it != "0.0.0.0" && it != "::" }
                    ?: "192.168.122.1"
            }

            Logger.d(TAG) { "Derived gateway $gatewayIp from link address ${linkAddress ?: "unknown"}" }
            NetworkInfo(gatewayIp = gatewayIp, linkAddress = linkAddress)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to detect network info" }
            null
        }
    }
}
