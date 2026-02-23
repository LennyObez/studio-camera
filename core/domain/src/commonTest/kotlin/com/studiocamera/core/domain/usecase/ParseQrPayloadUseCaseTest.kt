package com.studiocamera.core.domain.usecase

import com.studiocamera.core.domain.model.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParseQrPayloadUseCaseTest {

    private val fixedTime = 1_700_000_000L
    private val useCase = ParseQrPayloadUseCase(currentTimeSeconds = { fixedTime })

    // --- Valid JSON payloads ---

    @Test
    fun validJsonPayload_returnsSuccess() {
        val json = """
            {"v":1,"deviceId":"dev-1","deviceName":"Box A","endpoint":"https://192.168.1.10:8443","fingerprint":"AA:BB:CC","bindToken":"tok123","expiresAt":${fixedTime + 3600}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Success>(result)
        assertEquals("dev-1", result.payload.deviceId)
        assertEquals("Box A", result.payload.deviceName)
        assertEquals("https://192.168.1.10:8443", result.payload.endpoint)
    }

    @Test
    fun expiredPayload_returnsExpired() {
        val json = """
            {"v":1,"deviceId":"dev-1","deviceName":"Box A","endpoint":"https://host","fingerprint":"AA","bindToken":"tok","expiresAt":${fixedTime - 1}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Expired>(result)
    }

    @Test
    fun unsupportedVersion_returnsUnsupportedVersion() {
        val json = """
            {"v":99,"deviceId":"dev-1","deviceName":"Box A","endpoint":"https://host","fingerprint":"AA","bindToken":"tok","expiresAt":${fixedTime + 3600}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.UnsupportedVersion>(result)
        assertEquals(99, result.version)
    }

    @Test
    fun emptyDeviceId_returnsInvalid() {
        val json = """
            {"v":1,"deviceId":"","deviceName":"Box A","endpoint":"https://host","fingerprint":"AA","bindToken":"tok","expiresAt":${fixedTime + 3600}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Invalid>(result)
        assertTrue(result.reason.contains("Device ID"))
    }

    @Test
    fun emptyEndpoint_returnsInvalid() {
        val json = """
            {"v":1,"deviceId":"dev-1","deviceName":"Box A","endpoint":"","fingerprint":"AA","bindToken":"tok","expiresAt":${fixedTime + 3600}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Invalid>(result)
        assertTrue(result.reason.contains("Endpoint"))
    }

    @Test
    fun emptyFingerprint_returnsInvalid() {
        val json = """
            {"v":1,"deviceId":"dev-1","deviceName":"Box A","endpoint":"https://host","fingerprint":"","bindToken":"tok","expiresAt":${fixedTime + 3600}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Invalid>(result)
        assertTrue(result.reason.contains("Fingerprint"))
    }

    @Test
    fun emptyBindToken_returnsInvalid() {
        val json = """
            {"v":1,"deviceId":"dev-1","deviceName":"Box A","endpoint":"https://host","fingerprint":"AA","bindToken":"","expiresAt":${fixedTime + 3600}}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Invalid>(result)
        assertTrue(result.reason.contains("Bind token"))
    }

    // --- Sony Wi-Fi Direct format ---

    @Test
    fun sonyQrCode_returnsSonyDevice() {
        val raw = "W01:S:1YE1;P:KN9bWfc9;C:ILCE-7M3;M:D8106828244D;"

        val result = useCase(raw)
        assertIs<ParseResult.SonyDevice>(result)
        assertEquals("1YE1", result.ssidSuffix)
        assertEquals("KN9bWfc9", result.password)
        assertEquals("ILCE-7M3", result.modelName)
        assertEquals("D8106828244D", result.macAddress)
    }

    @Test
    fun sonyQrCode_withWhitespace_returnsSonyDevice() {
        val raw = "  W01:S:ABC1;P:pass1234;C:ILCE-7RM5;M:AABB11223344;  "

        val result = useCase(raw)
        assertIs<ParseResult.SonyDevice>(result)
        assertEquals("ABC1", result.ssidSuffix)
        assertEquals("pass1234", result.password)
        assertEquals("ILCE-7RM5", result.modelName)
    }

    @Test
    fun sonyQrCode_missingSSID_returnsUnrecognized() {
        val raw = "W01:P:pass;C:Model;M:MAC;"

        val result = useCase(raw)
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun sonyQrCode_missingPassword_returnsUnrecognized() {
        val raw = "W01:S:suffix;C:Model;M:MAC;"

        val result = useCase(raw)
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun sonyQrCode_missingModel_usesDefault() {
        val raw = "W01:S:suffix;P:pass;M:MAC;"

        val result = useCase(raw)
        assertIs<ParseResult.SonyDevice>(result)
        assertEquals("Unknown Sony", result.modelName)
    }

    @Test
    fun sonyQrCode_missingMac_usesEmpty() {
        val raw = "W01:S:suffix;P:pass;C:Alpha;"

        val result = useCase(raw)
        assertIs<ParseResult.SonyDevice>(result)
        assertEquals("", result.macAddress)
    }

    // --- Standard Wi-Fi QR format ---

    @Test
    fun standardWifiQr_returnsWifiCredentials() {
        val raw = "WIFI:T:WPA;S:MyNetwork;P:password123;H:false;;"
        val result = useCase(raw)
        assertIs<ParseResult.WifiCredentials>(result)
        assertEquals("MyNetwork", result.ssid)
        assertEquals("password123", result.password)
        assertEquals("WPA", result.authType)
    }

    @Test
    fun standardWifiQr_caseInsensitive_returnsWifiCredentials() {
        val raw = "wifi:T:WPA2;S:CameraNet;P:secret;;"
        val result = useCase(raw)
        assertIs<ParseResult.WifiCredentials>(result)
        assertEquals("CameraNet", result.ssid)
        assertEquals("secret", result.password)
    }

    @Test
    fun standardWifiQr_noPassword_returnsWifiCredentials() {
        val raw = "WIFI:T:nopass;S:OpenNetwork;;"
        val result = useCase(raw)
        assertIs<ParseResult.WifiCredentials>(result)
        assertEquals("OpenNetwork", result.ssid)
        assertEquals("", result.password)
    }

    @Test
    fun standardWifiQr_missingSsid_returnsUnrecognized() {
        val raw = "WIFI:T:WPA;P:password;;"
        val result = useCase(raw)
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun standardWifiQr_panasonicStyle_returnsWifiCredentials() {
        val raw = "WIFI:T:WPA;S:GH6-1234;P:lumix5678;;"
        val result = useCase(raw)
        assertIs<ParseResult.WifiCredentials>(result)
        assertEquals("GH6-1234", result.ssid)
        assertEquals("lumix5678", result.password)
    }

    // --- Unrecognized formats ---

    @Test
    fun randomText_returnsUnrecognized() {
        val result = useCase("Hello world, this is not a QR code")
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun url_returnsUnrecognized() {
        val result = useCase("https://example.com/some-page")
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun emptyString_returnsUnrecognized() {
        val result = useCase("")
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun malformedJson_returnsUnrecognized() {
        val result = useCase("{invalid json")
        assertIs<ParseResult.UnrecognizedFormat>(result)
    }

    @Test
    fun jsonWithUnknownKeys_stillParsesKnownFields() {
        val json = """
            {"v":1,"deviceId":"dev-1","deviceName":"Box","endpoint":"https://host","fingerprint":"AA","bindToken":"tok","expiresAt":${fixedTime + 3600},"extraField":"ignored"}
        """.trimIndent()

        val result = useCase(json)
        assertIs<ParseResult.Success>(result)
    }
}
