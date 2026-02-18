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
    val show916SafeZone: Boolean = false
)

enum class GridType(val label: String) {
    None("Off"),
    RuleOfThirds("3x3"),
    Grid2x2("2x2"),
    Grid6x4("6x4"),
    CenterCross("Center cross"),
    GoldenRatio("Golden ratio"),
    Diagonal("Diagonal"),
    Spiral("Golden spiral"),
    Ratio16x9("16:9"),
    Ratio9x16("9:16"),
    Ratio4x3("4:3"),
    Ratio3x2("3:2")
}
