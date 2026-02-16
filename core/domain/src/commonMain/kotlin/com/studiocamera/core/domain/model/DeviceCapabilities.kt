package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceCapabilities(
    val firmwareVersion: String = "",
    val minAppVersion: String = "",
    val supportsWebRTC: Boolean = false,
    val supportsRTSP: Boolean = false,
    val supportsMJPEG: Boolean = true,
    val supportsManualExposure: Boolean = false,
    val supportsManualFocus: Boolean = false,
    val supportsMediaDelete: Boolean = false,
    val supportsMdns: Boolean = true,
    val supportsUdpBroadcast: Boolean = false,
    val supportsBle: Boolean = false,
    val supportsNfc: Boolean = false,
    val maxResolution: String = "1920x1080",
    val supportedCodecs: List<String> = emptyList()
)
