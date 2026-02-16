package com.studiocamera.core.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.DiscoveredDevice
import com.studiocamera.core.domain.model.DiscoveryTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MdnsDiscoveryEngine(
    private val context: Context
) {
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    actual val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    actual val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        private const val SERVICE_TYPE = "_shutterpro._tcp."
        private const val TAG = "MdnsDiscovery"
    }

    actual fun startScanning() {
        if (_isScanning.value) return

        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Logger.d(TAG) { "mDNS discovery started for $serviceType" }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Logger.d(TAG) { "Service found: ${serviceInfo.serviceName}" }
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Logger.w(TAG) { "Resolve failed for ${info.serviceName}: $errorCode" }
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val device = DiscoveredDevice(
                            deviceId = info.serviceName,
                            deviceName = info.serviceName,
                            endpoint = "https://${info.host?.hostAddress}:${info.port}",
                            transport = DiscoveryTransport.MDNS,
                            lastSeenAt = System.currentTimeMillis()
                        )
                        val current = _discoveredDevices.value.toMutableList()
                        current.removeAll { it.deviceId == device.deviceId }
                        current.add(device)
                        _discoveredDevices.value = current
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Logger.d(TAG) { "Service lost: ${serviceInfo.serviceName}" }
                _discoveredDevices.value = _discoveredDevices.value
                    .filter { it.deviceId != serviceInfo.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Logger.d(TAG) { "mDNS discovery stopped" }
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logger.e(TAG) { "Discovery start failed: $errorCode" }
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logger.e(TAG) { "Discovery stop failed: $errorCode" }
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    actual fun stopScanning() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Logger.w(TAG) { "Error stopping discovery: ${e.message}" }
            }
        }
        discoveryListener = null
        _isScanning.value = false
    }
}
