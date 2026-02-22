package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val defaultGrid: Boolean = false,
    val defaultSafeZone: Boolean = false,
    val autoReconnect: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showBatteryWarnings: Boolean = true,
    val developerMode: Boolean = false,
    val onboardingCompleted: Boolean = false
)

@Serializable
enum class ThemeMode {
    Light,
    Dark,
    System
}
