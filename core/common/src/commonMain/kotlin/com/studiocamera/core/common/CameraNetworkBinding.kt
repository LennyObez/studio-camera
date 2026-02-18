package com.studiocamera.core.common

/**
 * Holds the camera Wi-Fi network reference so that HTTP clients can route
 * traffic through the bound network instead of the default network.
 *
 * On Android, [boundNetwork] is the [android.net.Network] obtained from
 * Wi-Fi Direct. It is stored as [Any?] to keep this object in commonMain
 * without Android dependencies.
 */
object CameraNetworkBinding {
    /** The [android.net.Network] for the camera Wi-Fi connection. */
    @Volatile
    var boundNetwork: Any? = null

    /** Callback invoked when the camera network changes (connected or lost). */
    @Volatile
    var onNetworkChanged: (() -> Unit)? = null
}
