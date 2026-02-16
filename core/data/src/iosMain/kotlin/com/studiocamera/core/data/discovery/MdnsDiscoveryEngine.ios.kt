package com.studiocamera.core.data.discovery

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.DiscoveredDevice
import com.studiocamera.core.domain.model.DiscoveryTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject

actual class MdnsDiscoveryEngine {
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    actual val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    actual val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val browser = NSNetServiceBrowser()
    private val browserDelegate = BrowserDelegate(this)

    companion object {
        private const val SERVICE_TYPE = "_shutterpro._tcp."
        private const val DOMAIN = "local."
        private const val TAG = "MdnsDiscovery"
    }

    init {
        browser.delegate = browserDelegate
    }

    actual fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        browser.searchForServicesOfType(SERVICE_TYPE, inDomain = DOMAIN)
        Logger.d(TAG) { "mDNS discovery started" }
    }

    actual fun stopScanning() {
        browser.stop()
        _isScanning.value = false
        Logger.d(TAG) { "mDNS discovery stopped" }
    }

    internal fun onServiceFound(service: NSNetService) {
        service.delegate = ServiceDelegate(this, service)
        service.resolveWithTimeout(10.0)
    }

    internal fun onServiceLost(name: String) {
        _discoveredDevices.value = _discoveredDevices.value.filter { it.deviceId != name }
    }

    internal fun onServiceResolved(service: NSNetService) {
        val hostName = service.hostName ?: return
        val device = DiscoveredDevice(
            deviceId = service.name,
            deviceName = service.name,
            endpoint = "https://$hostName:${service.port}",
            transport = DiscoveryTransport.MDNS,
            lastSeenAt = platform.Foundation.NSDate().timeIntervalSince1970.toLong() * 1000
        )
        val current = _discoveredDevices.value.toMutableList()
        current.removeAll { it.deviceId == device.deviceId }
        current.add(device)
        _discoveredDevices.value = current
    }

    private class BrowserDelegate(
        private val engine: MdnsDiscoveryEngine
    ) : NSObject(), NSNetServiceBrowserDelegateProtocol {
        override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
            engine.onServiceFound(didFindService)
        }

        override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
            engine.onServiceLost(didRemoveService.name)
        }
    }

    private class ServiceDelegate(
        private val engine: MdnsDiscoveryEngine,
        private val service: NSNetService
    ) : NSObject(), NSNetServiceDelegateProtocol {
        override fun netServiceDidResolveAddress(sender: NSNetService) {
            engine.onServiceResolved(sender)
        }

        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            Logger.w("MdnsDiscovery") { "Failed to resolve: ${sender.name}" }
        }
    }
}
