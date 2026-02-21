package com.studiocamera.core.data.media.sony

import co.touchlab.kermit.Logger
import com.studiocamera.core.data.camera.sony.SonyApiClient
import com.studiocamera.core.data.platform.PlatformDownloader
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.MediaFilter
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaPage
import com.studiocamera.core.domain.model.MediaSort
import com.studiocamera.core.domain.repository.DownloadProgress
import com.studiocamera.core.domain.repository.MediaRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SonyMediaRepository(
    private val apiClient: SonyApiClient,
    private val httpClient: HttpClient,
    private val endpoint: () -> String,
    private val platformDownloader: PlatformDownloader? = null
) : MediaRepository {

    companion object {
        private const val TAG = "SonyMedia"
        private const val AV_CONTENT_URI = "storage:memoryCard1" // Default for Sony
        /** Separator between Sony content URI and download URL in media item IDs. */
        private const val ID_SEPARATOR = "\n"
    }

    override suspend fun fetchPage(
        cursor: String?,
        filter: MediaFilter,
        sort: MediaSort,
        pageSize: Int
    ): ApiResult<MediaPage> = try {
        val ep = endpoint()
        if (ep.isBlank()) {
            ApiResult.Error(com.studiocamera.core.domain.model.SessionError.DeviceUnreachable)
        } else {
            // AvContent API is usually at /sony/avContent
            val avContentEp = ep.replace("/camera", "/avContent")
            
            // Ensure the camera is in ContentsTransfer mode
            // On newer models, this is required before using avContent APIs
            try {
                apiClient.call(ep, "setCameraFunction", listOf(JsonPrimitive("ContentsTransfer")))
                Logger.i(TAG) { "setCameraFunction ContentsTransfer succeeded" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.d(TAG) { "setCameraFunction ContentsTransfer not needed or failed: ${e.message}" }
            }

            val startIndex = cursor?.toIntOrNull() ?: 0
            
            // Note: sort is not natively supported by getContentList for Sony, 
            // the camera usually returns them in the order of creation.
            val view = when(filter) {
                MediaFilter.Videos -> "movie"
                MediaFilter.Photos -> "still"
                else -> "flat"
            }
            
            val params = listOf<JsonElement>(
                JsonObject(mapOf(
                    "uri" to JsonPrimitive(AV_CONTENT_URI),
                    "stIdx" to JsonPrimitive(startIndex),
                    "cnt" to JsonPrimitive(pageSize),
                    "view" to JsonPrimitive(view),
                    "sort" to JsonPrimitive("")
                ))
            )
            
            val results = apiClient.callForResults(avContentEp, "getContentList", params, "1.3")
            
            val items = mutableListOf<MediaItem>()
            val contentList = results?.firstOrNull()?.jsonArray
            
            contentList?.forEach { element ->
                if (element is JsonObject) {
                    val uri = element["uri"]?.jsonPrimitive?.content ?: ""
                    val title = element["title"]?.jsonPrimitive?.content ?: "Media"
                    val contentUrl = element["content"]?.jsonObject?.get("original")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                    val isVideo = element["content"]?.jsonObject?.get("original")?.jsonArray?.firstOrNull()?.jsonObject?.get("fileName")?.jsonPrimitive?.content?.endsWith(".mp4", ignoreCase = true) == true
                    
                    if (contentUrl != null) {
                        items.add(
                            MediaItem(
                                id = "$uri$ID_SEPARATOR$contentUrl",
                                filename = title,
                                type = if (isVideo) com.studiocamera.core.domain.model.MediaType.Video else com.studiocamera.core.domain.model.MediaType.Photo,
                                thumbnailUrl = element["content"]?.jsonObject?.get("thumbnail")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: "",
                                fullUrl = contentUrl,
                                sizeBytes = -1L,
                                capturedAt = 0L
                            )
                        )
                    }
                }
            }
            
            val nextCursor = if (items.size == pageSize) (startIndex + pageSize).toString() else null
            
            ApiResult.Success(
                MediaPage(
                    items = items,
                    nextCursor = nextCursor,
                    totalCount = 0
                )
            )
        }
    } catch (e: Exception) {
        Logger.e(TAG, e) { "Failed to fetch media page" }
        ApiResult.Error(com.studiocamera.core.domain.model.SessionError.Unknown(e))
    }

    override suspend fun getDetail(id: String): ApiResult<MediaItem> {
        // Sony API getContentList already returns all needed details
        // To properly implement this, we'd need to search for the specific URI or cache.
        // For simplicity, returning error as the app gets all info from the list endpoint.
        return ApiResult.Error(com.studiocamera.core.domain.model.SessionError.Unknown(Exception("Not supported as Sony returns details in list")))
    }

    override suspend fun downloadMedia(id: String): Flow<DownloadProgress> = flow {
        try {
            val urlToDownload = id.substringAfter(ID_SEPARATOR)
            
            if (!urlToDownload.startsWith("http")) {
                 throw Exception("URL not found for ID $id")
            }

            Logger.d(TAG) { "Downloading from $urlToDownload" }
            val response = httpClient.get(urlToDownload)
            val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()
            var downloaded = 0L

            val allBytes: ByteArray
            if (contentLength in 1..200_000_000L) {
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

            val filename = urlToDownload.substringAfterLast("/")
            val mediaType = if (filename.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "image/jpeg"

            val localPath = if (platformDownloader != null) {
                platformDownloader.save(filename, mediaType, allBytes)
            } else {
                "/downloads/$filename"
            }

            emit(DownloadProgress.Completed(localPath))
            Logger.d(TAG) { "Sony Media download completed: $filename ($downloaded bytes)" }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to download media" }
            emit(DownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }

    override suspend fun deleteMedia(id: String): ApiResult<Unit> = try {
        val ep = endpoint()
        if (ep.isBlank()) {
            ApiResult.Error(com.studiocamera.core.domain.model.SessionError.DeviceUnreachable)
        } else {
            val avContentEp = ep.replace("/camera", "/avContent")
            
            val params = listOf<JsonElement>(
                JsonObject(mapOf(
                    "uri" to JsonArray(listOf(JsonPrimitive(id.substringBefore(ID_SEPARATOR))))
                ))
            )
            
            apiClient.call(avContentEp, "deleteContent", params)
            ApiResult.Success(Unit)
        }
    } catch (e: Exception) {
        Logger.e(TAG, e) { "Failed to delete media $id" }
        ApiResult.Error(com.studiocamera.core.domain.model.SessionError.Unknown(e))
    }
}
