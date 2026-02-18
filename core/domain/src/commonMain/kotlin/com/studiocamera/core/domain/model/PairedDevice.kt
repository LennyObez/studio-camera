package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionType {
    @Deprecated("No longer supported — treated as WifiDirect")
    StudioCameraBox,
    WifiDirect
}

@Serializable
enum class CameraBrand { Sony, Canon, Nikon, Fujifilm, Panasonic, OmSystem, Unknown }

@Serializable
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val fingerprint: String,
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    val lastConnectedAt: Long = 0L,
    val isPrimary: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.WifiDirect,
    val cameraBrand: CameraBrand = CameraBrand.Unknown,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val customName: String? = null
) {
    val displayName: String get() = customName ?: deviceName
}

/**
 * Maps a raw camera model code (e.g. from QR code) to a user-friendly commercial name.
 * Falls back to "Brand ModelCode" if the model isn't in the lookup table.
 */
fun formatCameraDisplayName(brand: CameraBrand, modelCode: String): String {
    val commercial = cameraModelNames[modelCode.uppercase()]
    if (commercial != null) return commercial

    val brandPrefix = when (brand) {
        CameraBrand.Sony -> "Sony"
        CameraBrand.Canon -> "Canon"
        CameraBrand.Nikon -> "Nikon"
        CameraBrand.Fujifilm -> "Fujifilm"
        CameraBrand.Panasonic -> "Panasonic"
        CameraBrand.OmSystem -> "OM System"
        CameraBrand.Unknown -> return modelCode
    }
    return "$brandPrefix $modelCode"
}

private val cameraModelNames = mapOf(
    // ── Sony Alpha (current — Camera Remote SDK + JSON-RPC) ──
    "ILCE-1M2" to "Sony Alpha 1 II",
    "ILCE-1" to "Sony Alpha 1",
    "ILCE-9M3" to "Sony Alpha 9 III",
    "ILCE-9M2" to "Sony Alpha 9 II",
    "ILCE-9" to "Sony Alpha 9",
    "ILCE-7M5" to "Sony Alpha 7 V",
    "ILCE-7M4" to "Sony Alpha 7 IV",
    "ILCE-7M3" to "Sony Alpha 7 III",
    "ILCE-7M2" to "Sony Alpha 7 II",
    "ILCE-7RM5" to "Sony Alpha 7R V",
    "ILCE-7RM4A" to "Sony Alpha 7R IVA",
    "ILCE-7RM4" to "Sony Alpha 7R IV",
    "ILCE-7RM3A" to "Sony Alpha 7R IIIA",
    "ILCE-7RM3" to "Sony Alpha 7R III",
    "ILCE-7RM2" to "Sony Alpha 7R II",
    "ILCE-7SM3" to "Sony Alpha 7S III",
    "ILCE-7SM2" to "Sony Alpha 7S II",
    "ILCE-7CR" to "Sony Alpha 7CR",
    "ILCE-7CM2" to "Sony Alpha 7C II",
    "ILCE-7C" to "Sony Alpha 7C",
    "ILCE-7R" to "Sony Alpha 7R",
    "ILCE-7S" to "Sony Alpha 7S",
    "ILCE-7" to "Sony Alpha 7",
    // Sony Alpha (APS-C)
    "ILCE-6700" to "Sony Alpha 6700",
    "ILCE-6600" to "Sony Alpha 6600",
    "ILCE-6500" to "Sony Alpha 6500",
    "ILCE-6400A" to "Sony Alpha 6400",
    "ILCE-6400" to "Sony Alpha 6400",
    "ILCE-6300" to "Sony Alpha 6300",
    "ILCE-6100A" to "Sony Alpha 6100",
    "ILCE-6100" to "Sony Alpha 6100",
    "ILCE-6000" to "Sony Alpha 6000",
    "ILCE-5100" to "Sony Alpha 5100",
    "ILCE-5000" to "Sony Alpha 5000",
    "ILCE-3500" to "Sony Alpha 3500",
    "ILCE-3000" to "Sony Alpha 3000",
    "ILCE-QX1" to "Sony QX1",
    // Sony ZV / Vlog
    "ZV-E1" to "Sony ZV-E1",
    "ZV-E10M2" to "Sony ZV-E10 II",
    "ZV-E10" to "Sony ZV-E10",
    "ZV-1M2" to "Sony ZV-1 II",
    "ZV-1F" to "Sony ZV-1F",
    "ZV-1A" to "Sony ZV-1",
    "ZV-1" to "Sony ZV-1",
    // Sony Cyber-shot
    "DSC-RX1RM3" to "Sony RX1R III",
    "DSC-RX1RM2" to "Sony RX1R II",
    "DSC-RX0M2" to "Sony RX0 II",
    "DSC-RX100M7A" to "Sony RX100 VII",
    "DSC-RX100M7" to "Sony RX100 VII",
    "DSC-RX100M6" to "Sony RX100 VI",
    "DSC-RX100M5A" to "Sony RX100 VA",
    "DSC-RX100M5" to "Sony RX100 V",
    "DSC-RX100M4" to "Sony RX100 IV",
    "DSC-RX100M3" to "Sony RX100 III",
    "DSC-RX100M2" to "Sony RX100 II",
    "DSC-RX10M4" to "Sony RX10 IV",
    "DSC-RX10M3" to "Sony RX10 III",
    "DSC-RX10M2" to "Sony RX10 II",
    "DSC-RX10" to "Sony RX10",
    "DSC-HX99" to "Sony HX99",
    "DSC-HX400V" to "Sony HX400V",
    "DSC-HX90V" to "Sony HX90V",
    "DSC-HX80" to "Sony HX80",
    "DSC-HX60V" to "Sony HX60V",
    "DSC-WX500" to "Sony WX500",
    "DSC-QX100" to "Sony QX100",
    "DSC-QX30" to "Sony QX30",
    "DSC-QX10" to "Sony QX10",
    // Sony NEX (legacy)
    "NEX-7" to "Sony NEX-7",
    "NEX-6" to "Sony NEX-6",
    "NEX-5T" to "Sony NEX-5T",
    "NEX-5R" to "Sony NEX-5R",

    // ── Canon EOS R (mirrorless — CCAPI) ──
    "EOS R1" to "Canon EOS R1",
    "EOS R3" to "Canon EOS R3",
    "EOS R5 MARK II" to "Canon EOS R5 Mark II",
    "EOS R5 II" to "Canon EOS R5 Mark II",
    "EOS R5 C" to "Canon EOS R5 C",
    "EOS R5" to "Canon EOS R5",
    "EOS R6 MARK III" to "Canon EOS R6 Mark III",
    "EOS R6 III" to "Canon EOS R6 Mark III",
    "EOS R6 MARK II" to "Canon EOS R6 Mark II",
    "EOS R6 II" to "Canon EOS R6 Mark II",
    "EOS R6" to "Canon EOS R6",
    "EOS R7" to "Canon EOS R7",
    "EOS R8" to "Canon EOS R8",
    "EOS R10" to "Canon EOS R10",
    "EOS R50" to "Canon EOS R50",
    "EOS R100" to "Canon EOS R100",
    "EOS RP" to "Canon EOS RP",
    "EOS RA" to "Canon EOS Ra",
    "EOS R50 V" to "Canon EOS R50 V",
    // Canon EOS DSLR (CCAPI)
    "EOS-1D X MARK III" to "Canon EOS-1D X Mark III",
    "EOS-1D X MARK II" to "Canon EOS-1D X Mark II",
    "EOS-1D X" to "Canon EOS-1D X",
    "EOS 90D" to "Canon EOS 90D",
    "EOS 850D" to "Canon EOS 850D",
    "EOS 250D" to "Canon EOS 250D",
    // Canon EOS M (CCAPI)
    "EOS M50 MARK II" to "Canon EOS M50 Mark II",
    "EOS M6 MARK II" to "Canon EOS M6 Mark II",
    "EOS M200" to "Canon EOS M200",
    // Canon PowerShot (CCAPI)
    "POWERSHOT G5 X MARK II" to "Canon PowerShot G5 X Mark II",
    "POWERSHOT G7 X MARK III" to "Canon PowerShot G7 X Mark III",
    "POWERSHOT SX70 HS" to "Canon PowerShot SX70 HS",
    "POWERSHOT V10" to "Canon PowerShot V10",
    "POWERSHOT V1" to "Canon PowerShot V1",

    // ── Nikon Z (PTP/IP) ──
    "Z 9" to "Nikon Z 9",
    "Z 8" to "Nikon Z 8",
    "Z 7II" to "Nikon Z 7II",
    "Z 7" to "Nikon Z 7",
    "Z 6III" to "Nikon Z 6III",
    "Z 6II" to "Nikon Z 6II",
    "Z 6" to "Nikon Z 6",
    "Z 5II" to "Nikon Z 5II",
    "Z 5" to "Nikon Z 5",
    "Z 50II" to "Nikon Z 50II",
    "Z 50" to "Nikon Z 50",
    "Z 30" to "Nikon Z 30",
    "Z FC" to "Nikon Z fc",
    "ZFC" to "Nikon Z fc",
    "Z F" to "Nikon Z f",
    "ZF" to "Nikon Z f",

    // ── Panasonic / Lumix (cam.cgi HTTP) ──
    "DC-S5M2X" to "Panasonic Lumix S5 IIX",
    "DC-S5M2" to "Panasonic Lumix S5 II",
    "DC-S5" to "Panasonic Lumix S5",
    "DC-S9" to "Panasonic Lumix S9",
    "DC-S1R" to "Panasonic Lumix S1R",
    "DC-S1H" to "Panasonic Lumix S1H",
    "DC-S1" to "Panasonic Lumix S1",
    "DC-GH7" to "Panasonic Lumix GH7",
    "DC-GH6" to "Panasonic Lumix GH6",
    "DC-GH5M2" to "Panasonic Lumix GH5 II",
    "DC-GH5S" to "Panasonic Lumix GH5S",
    "DC-GH5" to "Panasonic Lumix GH5",
    "DC-G9M2" to "Panasonic Lumix G9 II",
    "DC-G9" to "Panasonic Lumix G9",
    "DC-G100" to "Panasonic Lumix G100",
    "DC-BGH1" to "Panasonic Lumix BGH1",
    "DC-BS1H" to "Panasonic Lumix BS1H",

    // ── Fujifilm X / GFX (custom binary protocol) ──
    "X-T50" to "Fujifilm X-T50",
    "X-T5" to "Fujifilm X-T5",
    "X-T4" to "Fujifilm X-T4",
    "X-T3" to "Fujifilm X-T3",
    "X-T30 II" to "Fujifilm X-T30 II",
    "X-T30" to "Fujifilm X-T30",
    "X-H2S" to "Fujifilm X-H2S",
    "X-H2" to "Fujifilm X-H2",
    "X-S20" to "Fujifilm X-S20",
    "X-S10" to "Fujifilm X-S10",
    "X-PRO3" to "Fujifilm X-Pro3",
    "X-E4" to "Fujifilm X-E4",
    "X-M5" to "Fujifilm X-M5",
    "X-A7" to "Fujifilm X-A7",
    "X100VI" to "Fujifilm X100VI",
    "X100V" to "Fujifilm X100V",
    "GFX100S II" to "Fujifilm GFX 100S II",
    "GFX100S" to "Fujifilm GFX 100S",
    "GFX100 II" to "Fujifilm GFX 100 II",
    "GFX50S II" to "Fujifilm GFX 50S II",
    "GFX50S" to "Fujifilm GFX 50S",
    "GFX50R" to "Fujifilm GFX 50R",

    // ── OM System / Olympus (HTTP CGI + XML) ──
    "OM-1 MARK II" to "OM System OM-1 Mark II",
    "OM-1 II" to "OM System OM-1 Mark II",
    "OM-1" to "OM System OM-1",
    "OM-5" to "OM System OM-5",
    "E-M1X" to "OM System E-M1X",
    "E-M1 MARK III" to "OM System E-M1 Mark III",
    "E-M1 III" to "OM System E-M1 Mark III",
    "E-M5 MARK III" to "OM System E-M5 Mark III",
    "E-M5 III" to "OM System E-M5 Mark III",
    "E-M10 MARK IV" to "OM System E-M10 Mark IV",
    "E-M10 IV" to "OM System E-M10 Mark IV",
    "E-P7" to "OM System PEN E-P7",
    "E-PL10" to "OM System PEN E-PL10",
)

fun detectCameraBrand(ssid: String): CameraBrand {
    val upper = ssid.uppercase()
    return when {
        // Sony: DIRECT-xxxx:ILCE-*, DSC-*, NEX-*, ZV-*, or contains "Sony"
        upper.contains("ILCE-") || upper.contains("DSC-") || upper.contains("NEX-") ||
            upper.contains("ZV-") || upper.contains("SONY") -> CameraBrand.Sony
        // Canon: contains "Canon" or "EOS"
        upper.contains("CANON") || upper.contains("EOS") -> CameraBrand.Canon
        // Nikon: contains "Nikon" (don't match bare D/Z prefixes — too broad)
        upper.contains("NIKON") -> CameraBrand.Nikon
        // Fujifilm: contains "FUJIFILM" or starts with X-T, X-S, X-H, X-E, X-M, X-A, X-PRO, X100, GFX
        upper.contains("FUJIFILM") ||
            upper.startsWith("X-T") || upper.startsWith("X-S") ||
            upper.startsWith("X-H") || upper.startsWith("X-E") ||
            upper.startsWith("X-M") || upper.startsWith("X-A") ||
            upper.startsWith("X-PRO") || upper.startsWith("X100") ||
            upper.startsWith("GFX") -> CameraBrand.Fujifilm
        // Panasonic/Lumix: contains "Panasonic", "LUMIX", "GH", "S1", "S5", "G9"
        upper.contains("PANASONIC") || upper.contains("LUMIX") ||
            upper.startsWith("GH") || upper.startsWith("S1") ||
            upper.startsWith("S5") || upper.startsWith("G9") -> CameraBrand.Panasonic
        // OM System: contains "OM-" or starts with "E-M", "E-P"
        upper.contains("OM-") || upper.startsWith("E-M") || upper.startsWith("E-P") -> CameraBrand.OmSystem
        // DIRECT- prefix alone is not enough to identify Sony (other brands use it too)
        else -> CameraBrand.Unknown
    }
}
