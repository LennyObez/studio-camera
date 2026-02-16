package com.studiocamera.feature.camera.presentation.overlay

import kotlinx.serialization.Serializable

@Serializable
data class OverlayConfig(
    val gridType: GridType = GridType.None,
    val showHistogram: Boolean = false,
    val showZebra: Boolean = false,
    val zebraThreshold: Int = 230,
    val showFocusPeaking: Boolean = false,
    val focusPeakingColor: Long = 0xFFFF0000,
    val showSafeZone: Boolean = false,
    val isDisabledByThermal: Boolean = false
)

enum class GridType {
    None,
    RuleOfThirds,
    CenterCross,
    GoldenRatio
}
