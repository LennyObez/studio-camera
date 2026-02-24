package com.studiocamera.core.data.repository

import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.repository.DeviceStorageRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairRepositoryImplTest {

    // ── Fake DeviceStorageRepository ──

    private class FakeDeviceStorage : DeviceStorageRepository {
        private val trustedFingerprints = mutableMapOf<String, String>()
        private val sessions = mutableMapOf<String, SessionInfo>()
        private val devices = mutableMapOf<String, PairedDevice>()
        private val wifiPasswords = mutableMapOf<String, String>()

        override suspend fun savePairedDevice(device: PairedDevice) {
            devices[device.deviceId] = device
        }
        override suspend fun getPairedDevices(): List<PairedDevice> = devices.values.toList()
        override suspend fun getPairedDevice(deviceId: String): PairedDevice? = devices[deviceId]
        override suspend fun removePairedDevice(deviceId: String) { devices.remove(deviceId) }
        override suspend fun saveWifiPassword(deviceId: String, password: String) {
            wifiPasswords[deviceId] = password
        }
        override suspend fun getWifiPassword(deviceId: String): String? = wifiPasswords[deviceId]
        override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {
            trustedFingerprints[deviceId] = fingerprint
        }
        override suspend fun getTrustedFingerprint(deviceId: String): String? =
            trustedFingerprints[deviceId]
        override suspend fun saveSessionInfo(info: SessionInfo) {
            sessions[info.deviceId] = info
        }
        override suspend fun getSessionInfo(deviceId: String): SessionInfo? = sessions[deviceId]
        override suspend fun clearSessionInfo(deviceId: String) { sessions.remove(deviceId) }
        override suspend fun clearAll() {
            devices.clear(); sessions.clear()
            trustedFingerprints.clear(); wifiPasswords.clear()
        }
    }

    // ── TestHarness ──

    private data class TestHarness(
        val repo: PairRepositoryImpl,
        val deviceStorage: FakeDeviceStorage
    )

    private fun createHarness(): TestHarness {
        val engine = MockEngine { _ ->
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(engine)
        val deviceStorage = FakeDeviceStorage()
        val repo = PairRepositoryImpl(httpClient, deviceStorage)
        return TestHarness(repo, deviceStorage)
    }

    // ── Tests ──

    @Test
    fun resolveEndpointWithValidUrlSucceeds() = runTest {
        val h = createHarness()

        // Should not throw — valid URL + mock health check returns 200
        h.repo.resolveEndpoint("http://192.168.1.1")
    }

    @Test
    fun resolveEndpointWithValidHttpsUrlSucceeds() = runTest {
        val h = createHarness()

        h.repo.resolveEndpoint("https://192.168.1.1")
    }

    @Test
    fun resolveEndpointWithValidHostnameSucceeds() = runTest {
        val h = createHarness()

        h.repo.resolveEndpoint("http://camera.local")
    }

    @Test
    fun resolveEndpointWithPortSucceeds() = runTest {
        val h = createHarness()

        h.repo.resolveEndpoint("http://192.168.1.1:8080")
    }

    @Test
    fun resolveEndpointWithEmptyUrlThrows() = runTest {
        val h = createHarness()

        val ex = assertFailsWith<IllegalArgumentException> {
            h.repo.resolveEndpoint("")
        }
        assertTrue(ex.message?.contains("empty") == true || ex.message?.contains("Endpoint") == true)
    }

    @Test
    fun resolveEndpointWithBlankUrlThrows() = runTest {
        val h = createHarness()

        assertFailsWith<IllegalArgumentException> {
            h.repo.resolveEndpoint("   ")
        }
    }

    @Test
    fun resolveEndpointWithInvalidUrlFormatThrows() = runTest {
        val h = createHarness()

        // No http:// or https:// prefix
        assertFailsWith<IllegalArgumentException> {
            h.repo.resolveEndpoint("ftp://192.168.1.1")
        }
    }

    @Test
    fun resolveEndpointWithMalformedUrlThrows() = runTest {
        val h = createHarness()

        assertFailsWith<IllegalArgumentException> {
            h.repo.resolveEndpoint("http://[garbage]")
        }
    }

    @Test
    fun resolveEndpointWithJustProtocolThrows() = runTest {
        val h = createHarness()

        assertFailsWith<IllegalArgumentException> {
            h.repo.resolveEndpoint("http://")
        }
    }

    @Test
    fun resolveEndpointWithPathSucceeds() = runTest {
        val h = createHarness()

        h.repo.resolveEndpoint("http://192.168.1.1/api")
    }

    @Test
    fun resolveEndpointWithNonHttpProtocolThrows() = runTest {
        val h = createHarness()

        assertFailsWith<IllegalArgumentException> {
            h.repo.resolveEndpoint("ws://192.168.1.1")
        }
    }
}
