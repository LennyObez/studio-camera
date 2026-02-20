package com.studiocamera.core.common

/**
 * Holds the camera Wi-Fi network reference so that HTTP clients can route
 * traffic through the bound network instead of the default network.
 *
 * On Android this wraps an [android.net.Network]. On iOS it's unused (null)
 * because iOS doesn't have the multi-network routing problem.
 *
 * The raw value is [Any?] in commonMain. Platform code should use the typed
 * extension (e.g. `boundAndroidNetwork` on Android) for compile-time safety.
 */
object CameraNetworkBinding {
    @Volatile
    var boundNetwork: Any? = null

    /** Callback invoked when the camera network changes (connected or lost). */
    @Volatile
    var onNetworkChanged: (() -> Unit)? = null

    /** True if a bound network is currently available. */
    val isBound: Boolean get() = boundNetwork != null
}
