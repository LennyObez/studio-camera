package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Result of API discovery.
 */
data class DiscoveryResult(
    val endpoint: String,
    val verified: Boolean = false,
    val supportsMjpeg: Boolean = false
)

/**
 * Discovers camera API endpoints by probing known ports and using SSDP.
 */
class BrandApiDiscovery(private val httpClient: HttpClient) {

    private val TAG = "BrandApiDiscovery"

    /**
     * Discovers the Sony Camera Remote API endpoint.
     *
     * Strategy (per attempt):
     * 1. SSDP M-SEARCH to discover the device descriptor, then parse the API URL
     * 2. HTTP probes to known ports (8080, 10000) on candidate gateway IPs
     *
     * @param gatewayIp gateway IP derived from the Wi-Fi Direct link address
     * @param linkAddress IP assigned to this phone on the camera network (for logging)
     */
    suspend fun discoverSony(gatewayIp: String, linkAddress: String? = null): DiscoveryResult? {
        Logger.d(TAG) { "Sony discovery — gateway: $gatewayIp, link: ${linkAddress ?: "none"}" }

        // Build candidate gateway IPs
        val candidateIps = mutableListOf(gatewayIp)
        listOf("192.168.122.1", "192.168.49.1", "192.168.1.1", "10.0.0.1").forEach { ip ->
            if (ip != gatewayIp) candidateIps.add(ip)
        }

        // Combine IPs with known Sony API ports
        val candidateEndpoints = candidateIps.flatMap { ip ->
            listOf("http://$ip:8080/sony/camera", "http://$ip:10000/sony/camera")
        }

        Logger.d(TAG) { "Probing ${candidateEndpoints.size} candidate endpoints" }

        val retryDelays = listOf(0L, 1000L, 2000L, 3000L, 5000L)

        for (attempt in 1..retryDelays.size) {
            if (attempt > 1) delay(retryDelays[attempt - 1])
            Logger.d(TAG) { "Discovery attempt $attempt" }

            // Try SSDP first — the official discovery mechanism
            val ssdpResult = performSsdpDiscovery(gatewayIp)
            if (ssdpResult != null) {
                return DiscoveryResult(endpoint = ssdpResult, verified = true, supportsMjpeg = true)
            }

            // Fall back to direct HTTP probes
            for (endpoint in candidateEndpoints) {
                val result = probeSonyEndpoint(endpoint)
                if (result != null) {
                    Logger.i(TAG) { "Sony API discovered at $endpoint (verified)" }
                    return result
                }
            }

            Logger.w(TAG) { "All probes failed on attempt $attempt" }
        }

        // All retries exhausted — return unverified fallback
        val fallback = "http://$gatewayIp:8080/sony/camera"
        Logger.w(TAG) { "Discovery unverified after ${retryDelays.size} attempts, using fallback: $fallback" }
        return DiscoveryResult(endpoint = fallback, verified = false, supportsMjpeg = true)
    }

    /**
     * Probes a Sony Camera Remote API endpoint with a JSON-RPC call.
     */
    private suspend fun probeSonyEndpoint(endpoint: String): DiscoveryResult? {
        return try {
            val body = buildJsonObject {
                put("method", "getAvailableApiList")
                put("params", JsonArray(emptyList()))
                put("id", 1)
                put("version", "1.0")
            }

            val response: HttpResponse = httpClient.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }

            if (response.status.value in 200..299) {
                val json = response.body<JsonObject>()
                if (json.containsKey("result") || json.containsKey("id")) {
                    Logger.d(TAG) { "Sony probe succeeded at $endpoint" }
                    return DiscoveryResult(endpoint = endpoint, verified = true, supportsMjpeg = true)
                }
            }
            null
        } catch (e: Exception) {
            Logger.d(TAG) { "Sony probe at $endpoint failed: ${e.message}" }
            null
        }
    }

    /**
     * Sends an SSDP M-SEARCH to discover the Sony device descriptor URL,
     * then parses the camera API endpoint from the XML.
     */
    private suspend fun performSsdpDiscovery(targetIp: String): String? {
        val locationUrl = SsdpScanner().discoverLocationUrl(targetIp) ?: return null
        Logger.i(TAG) { "SSDP discovered location: $locationUrl" }
        return fetchApiUrlFromDeviceDescription(locationUrl)
    }

    /**
     * Fetches the Sony device descriptor XML and extracts the camera API URL.
     *
     * Uses regex instead of an XML parser because kotlinx-serialization does not
     * support XML, and adding a full XML parser dependency is not justified for
     * parsing a single element.
     */
    private suspend fun fetchApiUrlFromDeviceDescription(xmlUrl: String): String? {
        return try {
            val response: HttpResponse = httpClient.get(xmlUrl)
            if (response.status.value !in 200..299) return null

            val xml = response.body<String>()
            val pattern = Regex(
                "<av:X_ScalarWebAPI_ServiceType>camera</av:X_ScalarWebAPI_ServiceType>" +
                    "\\s*<av:X_ScalarWebAPI_ActionList_URL>(.*?)</av:X_ScalarWebAPI_ActionList_URL>",
                RegexOption.IGNORE_CASE
            )
            val actionUrl = pattern.find(xml)?.groupValues?.get(1) ?: return null
            val endpoint = if (actionUrl.endsWith("/")) "${actionUrl}camera" else "$actionUrl/camera"
            Logger.i(TAG) { "Parsed Sony API endpoint: $endpoint" }
            endpoint
        } catch (e: Exception) {
            Logger.d(TAG) { "Failed to fetch device descriptor at $xmlUrl: ${e.message}" }
            null
        }
    }
}
