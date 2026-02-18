package com.studiocamera.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraBrandTest {

    @Test
    fun detectSony_fromDirectSsid() {
        assertEquals(CameraBrand.Sony, detectCameraBrand("DIRECT-1YE1:ILCE-7M3"))
    }

    @Test
    fun detectCanon_fromCanonSsid() {
        assertEquals(CameraBrand.Canon, detectCameraBrand("EOS_R5_Canon0A"))
    }

    @Test
    fun detectFujifilm_fromFujifilmSsid() {
        assertEquals(CameraBrand.Fujifilm, detectCameraBrand("FUJIFILM-XT5-1234"))
    }

    @Test
    fun detectPanasonic_fromLumixSsid() {
        assertEquals(CameraBrand.Panasonic, detectCameraBrand("LUMIX-S5IIX"))
    }

    @Test
    fun detectPanasonic_fromGhSsid() {
        assertEquals(CameraBrand.Panasonic, detectCameraBrand("GH6-1234"))
    }

    @Test
    fun detectOmSystem_fromOmSsid() {
        assertEquals(CameraBrand.OmSystem, detectCameraBrand("OM-1_A_1234"))
    }

    @Test
    fun detectOmSystem_fromEmSsid() {
        assertEquals(CameraBrand.OmSystem, detectCameraBrand("E-M1X_B_5678"))
    }

    @Test
    fun detectNikon_fromNikonSsid() {
        assertEquals(CameraBrand.Nikon, detectCameraBrand("Nikon_Z8_1234"))
    }

    @Test
    fun detectNikon_bareDzPrefixes_doNotMatch() {
        // Bare D/Z + digits are too broad — must contain "Nikon" explicitly
        assertEquals(CameraBrand.Unknown, detectCameraBrand("D850-WIFI"))
        assertEquals(CameraBrand.Unknown, detectCameraBrand("Z6_Network"))
    }

    @Test
    fun detectUnknown_fromGenericSsid() {
        assertEquals(CameraBrand.Unknown, detectCameraBrand("MyHomeNetwork"))
    }

    // --- formatCameraDisplayName tests ---

    @Test
    fun formatDisplayName_knownSonyModel() {
        assertEquals("Sony Alpha 7 III", formatCameraDisplayName(CameraBrand.Sony, "ILCE-7M3"))
    }

    @Test
    fun formatDisplayName_knownSonyModel_caseInsensitive() {
        assertEquals("Sony Alpha 7 III", formatCameraDisplayName(CameraBrand.Sony, "ilce-7m3"))
    }

    @Test
    fun formatDisplayName_unknownSonyModel_fallsBackToBrandPrefix() {
        assertEquals("Sony ILCE-9999", formatCameraDisplayName(CameraBrand.Sony, "ILCE-9999"))
    }

    @Test
    fun formatDisplayName_knownCanonModel() {
        assertEquals("Canon EOS R5", formatCameraDisplayName(CameraBrand.Canon, "EOS R5"))
    }

    @Test
    fun formatDisplayName_unknownBrand_returnsModelCodeOnly() {
        assertEquals("SomeModel", formatCameraDisplayName(CameraBrand.Unknown, "SomeModel"))
    }

    @Test
    fun formatDisplayName_sonyAlpha1II() {
        assertEquals("Sony Alpha 1 II", formatCameraDisplayName(CameraBrand.Sony, "ILCE-1M2"))
    }

    @Test
    fun formatDisplayName_sonyAlpha7V() {
        assertEquals("Sony Alpha 7 V", formatCameraDisplayName(CameraBrand.Sony, "ILCE-7M5"))
    }

    @Test
    fun formatDisplayName_nikonZf() {
        assertEquals("Nikon Z f", formatCameraDisplayName(CameraBrand.Nikon, "Z F"))
    }

    @Test
    fun formatDisplayName_fujifilmX100VI() {
        assertEquals("Fujifilm X100VI", formatCameraDisplayName(CameraBrand.Fujifilm, "X100VI"))
    }

    @Test
    fun formatDisplayName_omSystem() {
        assertEquals("OM System OM-1 Mark II", formatCameraDisplayName(CameraBrand.OmSystem, "OM-1 Mark II"))
    }

    // --- Additional brand detection tests ---

    @Test
    fun detectSony_fromZvSsid() {
        assertEquals(CameraBrand.Sony, detectCameraBrand("DIRECT-abcd:ZV-E1"))
    }

    @Test
    fun detectFujifilm_fromX100Ssid() {
        assertEquals(CameraBrand.Fujifilm, detectCameraBrand("X100VI-1234"))
    }

    @Test
    fun detectFujifilm_fromGfxSsid() {
        assertEquals(CameraBrand.Fujifilm, detectCameraBrand("GFX100S-WIFI"))
    }
}
