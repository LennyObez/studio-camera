package com.studiocamera.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val id: String,
    val filename: String,
    val type: MediaType,
    val thumbnailUrl: String,
    val fullUrl: String,
    val sizeBytes: Long,
    val capturedAt: Long,
    val durationMs: Long? = null,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
enum class MediaType {
    Photo,
    Video
}

@Serializable
data class MediaPage(
    val items: List<MediaItem>,
    val nextCursor: String? = null,
    val totalCount: Int = 0
)

enum class MediaFilter {
    All,
    Photos,
    Videos
}

enum class MediaSort {
    DateDesc,
    DateAsc,
    SizeDesc,
    SizeAsc
}
