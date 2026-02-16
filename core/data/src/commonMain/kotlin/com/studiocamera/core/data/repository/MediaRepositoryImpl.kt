package com.studiocamera.core.data.repository

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.MediaFilter
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaPage
import com.studiocamera.core.domain.model.MediaSort
import com.studiocamera.core.domain.repository.DownloadProgress
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.network.ApiEndpoints
import com.studiocamera.core.network.safeApiCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaRepositoryImpl(
    private val httpClient: HttpClient,
    private val endpoint: () -> String,
    private val accessToken: () -> String?
) : MediaRepository {

    override suspend fun fetchPage(
        cursor: String?,
        filter: MediaFilter,
        sort: MediaSort,
        pageSize: Int
    ): ApiResult<MediaPage> = safeApiCall {
        val response = httpClient.get("${endpoint()}${ApiEndpoints.MEDIA_LIST}") {
            accessToken()?.let { bearerAuth(it) }
            cursor?.let { parameter("cursor", it) }
            parameter("filter", filter.name.lowercase())
            parameter("sort", sort.name.lowercase())
            parameter("limit", pageSize)
        }
        response.body<MediaPage>()
    }

    override suspend fun getDetail(id: String): ApiResult<MediaItem> = safeApiCall {
        val response = httpClient.get("${endpoint()}${ApiEndpoints.mediaDetail(id)}") {
            accessToken()?.let { bearerAuth(it) }
        }
        response.body<MediaItem>()
    }

    override suspend fun downloadMedia(id: String): Flow<DownloadProgress> = flow {
        try {
            val response = httpClient.get("${endpoint()}${ApiEndpoints.mediaDownload(id)}") {
                accessToken()?.let { bearerAuth(it) }
            }

            val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()
            var downloaded = 0L

            val buffer = ByteArray(8192)
            // Read data and emit progress
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) break
                downloaded += read

                emit(DownloadProgress.InProgress(
                    bytesDownloaded = downloaded,
                    totalBytes = contentLength
                ))
            }

            // Save to platform storage (expect/actual needed for actual save)
            val localPath = saveToPlatformStorage(id, buffer)
            emit(DownloadProgress.Completed(localPath))
            Logger.d("Media") { "Download completed: $id ($downloaded bytes)" }
        } catch (e: Exception) {
            Logger.e("Media", e) { "Download failed: $id" }
            emit(DownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }

    override suspend fun deleteMedia(id: String): ApiResult<Unit> = safeApiCall {
        httpClient.delete("${endpoint()}${ApiEndpoints.mediaDelete(id)}") {
            accessToken()?.let { bearerAuth(it) }
        }
        Logger.d("Media") { "Deleted media: $id" }
    }

    private fun saveToPlatformStorage(id: String, data: ByteArray): String {
        // Platform-specific save handled by expect/actual DownloadManager in Phase 4
        return "/downloads/$id"
    }
}
