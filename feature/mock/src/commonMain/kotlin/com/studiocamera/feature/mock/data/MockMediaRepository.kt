package com.studiocamera.feature.mock.data

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.MediaFilter
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaPage
import com.studiocamera.core.domain.model.MediaSort
import com.studiocamera.core.domain.model.MediaType
import com.studiocamera.core.domain.repository.DownloadProgress
import com.studiocamera.core.domain.repository.MediaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockMediaRepository : MediaRepository {

    private val mockMedia = buildList {
        repeat(8) { i ->
            add(
                MediaItem(
                    id = "photo_${i + 1}",
                    filename = "IMG_${1000 + i}.jpg",
                    type = MediaType.Photo,
                    thumbnailUrl = "",
                    fullUrl = "",
                    sizeBytes = (2_000_000L..8_000_000L).random(),
                    capturedAt = System.currentTimeMillis() - (i * 3_600_000L),
                    width = 4000,
                    height = 3000
                )
            )
        }
        repeat(4) { i ->
            add(
                MediaItem(
                    id = "video_${i + 1}",
                    filename = "VID_${2000 + i}.mp4",
                    type = MediaType.Video,
                    thumbnailUrl = "",
                    fullUrl = "",
                    sizeBytes = (50_000_000L..500_000_000L).random(),
                    capturedAt = System.currentTimeMillis() - ((8 + i) * 3_600_000L),
                    durationMs = (30_000L..300_000L).random(),
                    width = 1920,
                    height = 1080
                )
            )
        }
    }

    private val deletedIds = mutableSetOf<String>()

    override suspend fun fetchPage(
        cursor: String?,
        filter: MediaFilter,
        sort: MediaSort,
        pageSize: Int
    ): ApiResult<MediaPage> {
        delay(300) // Simulate network

        val filtered = mockMedia
            .filter { it.id !in deletedIds }
            .filter { item ->
                when (filter) {
                    MediaFilter.All -> true
                    MediaFilter.Photos -> item.type == MediaType.Photo
                    MediaFilter.Videos -> item.type == MediaType.Video
                }
            }
            .let { items ->
                when (sort) {
                    MediaSort.DateDesc -> items.sortedByDescending { it.capturedAt }
                    MediaSort.DateAsc -> items.sortedBy { it.capturedAt }
                    MediaSort.SizeDesc -> items.sortedByDescending { it.sizeBytes }
                    MediaSort.SizeAsc -> items.sortedBy { it.sizeBytes }
                }
            }

        val startIndex = cursor?.toIntOrNull() ?: 0
        val endIndex = minOf(startIndex + pageSize, filtered.size)
        val page = filtered.subList(startIndex, endIndex)
        val nextCursor = if (endIndex < filtered.size) endIndex.toString() else null

        return ApiResult.Success(
            MediaPage(
                items = page,
                nextCursor = nextCursor,
                totalCount = filtered.size
            )
        )
    }

    override suspend fun getDetail(id: String): ApiResult<MediaItem> {
        delay(100)
        val item = mockMedia.find { it.id == id }
        return if (item != null) {
            ApiResult.Success(item)
        } else {
            ApiResult.Error(com.studiocamera.core.domain.model.SessionError.Unknown())
        }
    }

    override suspend fun downloadMedia(id: String): Flow<DownloadProgress> = flow {
        val item = mockMedia.find { it.id == id }
            ?: throw IllegalArgumentException("Media not found: $id")

        val totalBytes = item.sizeBytes
        var downloaded = 0L
        val chunkSize = totalBytes / 10

        repeat(10) {
            delay(200) // Simulate download speed
            downloaded = minOf(downloaded + chunkSize, totalBytes)
            emit(DownloadProgress.InProgress(downloaded, totalBytes))
        }

        emit(DownloadProgress.Completed("/mock/downloads/${item.filename}"))
    }

    override suspend fun deleteMedia(id: String): ApiResult<Unit> {
        delay(200)
        deletedIds.add(id)
        return ApiResult.Success(Unit)
    }

    // KMP-safe random for Long ranges
    private fun LongRange.random(): Long {
        return first + (kotlin.random.Random.nextLong() % (last - first + 1)).let {
            if (it < 0) it + (last - first + 1) else it
        }
    }

    // KMP-safe System.currentTimeMillis() equivalent
    private object System {
        fun currentTimeMillis(): Long = com.studiocamera.core.common.currentTimeMillis()
    }
}
