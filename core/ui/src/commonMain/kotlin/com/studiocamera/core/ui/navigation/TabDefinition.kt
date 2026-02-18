package com.studiocamera.core.ui.navigation

enum class Tab(
    val label: String,
    val contentDescription: String,
    val requiresConnection: Boolean
) {
    Home(
        label = "Home",
        contentDescription = "Home tab - your cameras and pairing",
        requiresConnection = false
    ),
    Discover(
        label = "Discover",
        contentDescription = "Discovery tab - find devices on network",
        requiresConnection = false
    ),
    Camera(
        label = "Camera",
        contentDescription = "Camera tab - live view and controls",
        requiresConnection = true
    ),
    Media(
        label = "Media",
        contentDescription = "Media tab - browse and download media",
        requiresConnection = true
    ),
    Settings(
        label = "Settings",
        contentDescription = "Settings tab - app preferences",
        requiresConnection = false
    ),
    Mock(
        label = "Mock",
        contentDescription = "Mock tab - offline simulator",
        requiresConnection = false
    )
}
