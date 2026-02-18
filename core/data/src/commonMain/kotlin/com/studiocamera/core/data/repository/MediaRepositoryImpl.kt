package com.studiocamera.core.data.repository

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.platform.PlatformDownloader
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
    private val accessToken: () -> String?,
    private val platformDownloader: PlatformDownloader? = null
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

            // Pre-allocate if content length is known, otherwise grow incrementally.
            // Use a single byte array to avoid double-buffering.
            val allBytes: ByteArray
            if (contentLength in 1..200_000_000L) {
                // Known size: stream directly into pre-allocated array
                allBytes = ByteArray(contentLength.toInt())
                var offset = 0
                val buffer = ByteArray(65536)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    buffer.copyInto(allBytes, offset, 0, read)
                    offset += read
                    downloaded += read
                    emit(DownloadProgress.InProgress(bytesDownloaded = downloaded, totalBytes = contentLength))
                }
            } else {
                // Unknown size: collect chunks then merge once
                val chunks = mutableListOf<ByteArray>()
                val buffer = ByteArray(65536)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    downloaded += read
                    chunks.add(buffer.copyOfRange(0, read))
                    emit(DownloadProgress.InProgress(bytesDownloaded = downloaded, totalBytes = contentLength))
                }
                allBytes = ByteArray(downloaded.toInt()).also { dest ->
                    var offset = 0
                    for (chunk in chunks) {
                        chunk.copyInto(dest, offset)
                        offset += chunk.size
                    }
                }
            }

            // Determine filename from media detail or use ID
            val detail = getDetail(id)
            val filename = (detail as? ApiResult.Success)?.data?.filename ?: "$id.jpg"
            val mediaType = if (filename.endsWith(".mp4")) "video/mp4" else "image/jpeg"

            val localPath = if (platformDownloader != null) {
                platformDownloader.save(filename, mediaType, allBytes)
            } else {
                "/downloads/$filename"
            }

            emit(DownloadProgress.Completed(localPath))
            Logger.d("Media") { "Download completed: $id ($downloaded bytes)" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
}
