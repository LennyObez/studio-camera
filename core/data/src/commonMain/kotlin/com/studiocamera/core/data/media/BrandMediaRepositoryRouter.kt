package com.studiocamera.core.data.media

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.MediaFilter
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaPage
import com.studiocamera.core.domain.model.MediaSort
import com.studiocamera.core.domain.repository.DownloadProgress
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class BrandMediaRepositoryRouter(
    private val connectionStateManager: ConnectionStateManager,
    private val brandRepositories: Map<CameraBrand, MediaRepository>,
    private val defaultRepository: MediaRepository
) : MediaRepository {

    private fun currentRepo(): MediaRepository {
        val brand = connectionStateManager.connectedDevice.value?.cameraBrand ?: CameraBrand.Unknown
        return brandRepositories[brand] ?: defaultRepository
    }

    override suspend fun fetchPage(
        cursor: String?,
        filter: MediaFilter,
        sort: MediaSort,
        pageSize: Int
    ): ApiResult<MediaPage> = currentRepo().fetchPage(cursor, filter, sort, pageSize)

    override suspend fun getDetail(id: String): ApiResult<MediaItem> = currentRepo().getDetail(id)

    override suspend fun downloadMedia(id: String): Flow<DownloadProgress> {
        return try {
            currentRepo().downloadMedia(id)
        } catch (e: Exception) {
            Logger.e("MediaRouter", e) { "Failed to download media $id" }
            emptyFlow()
        }
    }

    override suspend fun deleteMedia(id: String): ApiResult<Unit> = currentRepo().deleteMedia(id)
}
