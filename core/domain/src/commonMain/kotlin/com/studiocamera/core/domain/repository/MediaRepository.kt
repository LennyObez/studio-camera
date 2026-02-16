package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.MediaFilter
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaPage
import com.studiocamera.core.domain.model.MediaSort
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun fetchPage(
        cursor: String? = null,
        filter: MediaFilter = MediaFilter.All,
        sort: MediaSort = MediaSort.DateDesc,
        pageSize: Int = 30
    ): ApiResult<MediaPage>

    suspend fun getDetail(id: String): ApiResult<MediaItem>

    suspend fun downloadMedia(id: String): Flow<DownloadProgress>

    suspend fun deleteMedia(id: String): ApiResult<Unit>
}

sealed class DownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress() {
        val percent: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }
    data class Completed(val localPath: String) : DownloadProgress()
    data class Failed(val reason: String) : DownloadProgress()
}
