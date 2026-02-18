package com.studiocamera.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studiocamera.core.common.platform.ShareHandler
import com.studiocamera.core.designsystem.component.ConnectionGate
import com.studiocamera.core.designsystem.component.rememberSessionErrorState
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.MediaFilter
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaType
import com.studiocamera.core.domain.repository.DownloadProgress
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.domain.session.SessionManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MediaScreen(
    connectionState: ConnectionState,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionManager: SessionManager = koinInject()
    val errorState by rememberSessionErrorState(sessionManager, connectionState)
    val retryScope = rememberCoroutineScope()

    ConnectionGate(
        connectionState = connectionState,
        featureName = "Media",
        onNavigateToHome = onNavigateToHome,
        modifier = modifier,
        lastError = errorState.lastError,
        reconnectAttempt = errorState.reconnectAttempt,
        onRetry = { retryScope.launch { sessionManager.reconnect() } },
        onDismissError = {}
    ) {
        MediaContent()
    }
}

@Composable
private fun MediaContent() {
    val mediaRepository: MediaRepository = koinInject()
    val shareHandler: ShareHandler = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sharingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val shareItem: (MediaItem) -> Unit = { item ->
        scope.launch {
            sharingIds = sharingIds + item.id
            var shareFailed = false
            mediaRepository.downloadMedia(item.id).collect { progress ->
                when (progress) {
                    is DownloadProgress.Completed -> {
                        sharingIds = sharingIds - item.id
                        val mimeType = if (item.type == MediaType.Photo) "image/jpeg" else "video/mp4"
                        shareHandler.share(progress.localPath, mimeType)
                    }
                    is DownloadProgress.Failed -> {
                        sharingIds = sharingIds - item.id
                        shareFailed = true
                    }
                    is DownloadProgress.InProgress -> {}
                }
            }
            if (shareFailed) {
                snackbarHostState.showSnackbar("Failed to prepare ${item.filename} for sharing")
            }
        }
    }

    var selectedFilter by remember { mutableStateOf(MediaFilter.All) }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalCount by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
    var downloadingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Preview state
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    var loadError by remember { mutableStateOf<String?>(null) }

    // Load media on filter change
    LaunchedEffect(selectedFilter) {
        isLoading = true
        loadError = null
        val result = mediaRepository.fetchPage(filter = selectedFilter)
        when (result) {
            is ApiResult.Success -> {
                mediaItems = result.data.items
                totalCount = result.data.totalCount
            }
            is ApiResult.Error -> {
                loadError = "Failed to load media"
            }
        }
        isLoading = false
    }

    // Show preview if active
    previewIndex?.let { index ->
        MediaPreviewScreen(
            items = mediaItems,
            initialIndex = index,
            onBack = { previewIndex = null },
            onDownload = { item ->
                scope.launch {
                    downloadingIds = downloadingIds + item.id
                    mediaRepository.downloadMedia(item.id).collect { progress ->
                        when (progress) {
                            is DownloadProgress.Completed -> {
                                downloadingIds = downloadingIds - item.id
                                snackbarHostState.showSnackbar("Saved to gallery")
                            }
                            is DownloadProgress.Failed -> {
                                downloadingIds = downloadingIds - item.id
                                snackbarHostState.showSnackbar("Download failed: ${progress.reason}")
                            }
                            is DownloadProgress.InProgress -> { /* progress tracked by ID */ }
                        }
                    }
                }
            },
            onShare = shareItem,
            onDelete = { item ->
                deleteTarget = item
            }
        )
        return
    }

    // Delete confirmation dialog
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${item.filename}?") },
            text = { Text("This will permanently delete the file from the device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleteResult = mediaRepository.deleteMedia(item.id)
                            if (deleteResult is ApiResult.Error) {
                                snackbarHostState.showSnackbar("Failed to delete ${item.filename}")
                            } else {
                                val result = mediaRepository.fetchPage(filter = selectedFilter)
                                if (result is ApiResult.Success) {
                                    mediaItems = result.data.items
                                    totalCount = result.data.totalCount
                                }
                                previewIndex = null
                            }
                        }
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                when (filter) {
                                    MediaFilter.All -> "All ($totalCount)"
                                    MediaFilter.Photos -> "Photos"
                                    MediaFilter.Videos -> "Videos"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "No media",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No media yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Capture photos or videos from the Camera tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(mediaItems, key = { _, item -> item.id }) { itemIndex, item ->
                        MediaGridItem(
                            item = item,
                            isDownloading = item.id in downloadingIds,
                            onTap = { previewIndex = itemIndex },
                            onDelete = { deleteTarget = item },
                            onDownload = {
                                scope.launch {
                                    downloadingIds = downloadingIds + item.id
                                    mediaRepository.downloadMedia(item.id).collect { progress ->
                                        when (progress) {
                                            is DownloadProgress.Completed -> {
                                                downloadingIds = downloadingIds - item.id
                                                snackbarHostState.showSnackbar("Saved to gallery")
                                            }
                                            is DownloadProgress.Failed -> {
                                                downloadingIds = downloadingIds - item.id
                                                snackbarHostState.showSnackbar("Download failed: ${progress.reason}")
                                            }
                                            is DownloadProgress.InProgress -> {}
                                        }
                                    }
                                }
                            },
                            onShare = { shareItem(item) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MediaGridItem(
    item: MediaItem,
    isDownloading: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap)
    ) {
        // Thumbnail placeholder
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (item.type == MediaType.Photo) Icons.Default.Image else Icons.Default.Videocam,
                contentDescription = if (item.type == MediaType.Photo) "Photo" else "Video",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Download progress overlay
        if (isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }

        // Video duration badge
        val duration = item.durationMs
        if (item.type == MediaType.Video && duration != null) {
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Action buttons (overlay top-right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
        ) {
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${item.filename}",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share ${item.filename}",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${item.filename}",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

