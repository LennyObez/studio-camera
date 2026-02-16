package com.studiocamera.core.ui.navigation

enum class Tab(
    val label: String,
    val contentDescription: String,
    val requiresConnection: Boolean
) {
    Pair(
        label = "Pair",
        contentDescription = "Pair tab - connect to a device",
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
    Mock(
        label = "Mock",
        contentDescription = "Mock tab - offline simulator",
        requiresConnection = false
    )
}
