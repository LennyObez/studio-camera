package com.studiocamera.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.MediaItem
import com.studiocamera.core.domain.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onDownload: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size }
    )

    val currentItem = items.getOrNull(pagerState.currentPage)
    var showMetadata by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = currentItem?.filename ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = { showMetadata = !showMetadata }) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (showMetadata) "Hide metadata" else "Show metadata",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        )

        // Metadata panel
        if (showMetadata && currentItem != null) {
            MetadataPanel(item = currentItem)
        }

        // Pager with zoomable content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val item = items[page]
            ZoomablePreview(item = item)
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            currentItem?.let { item ->
                IconButton(onClick = { onDownload(item) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download to device",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Save", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                IconButton(onClick = { onShare(item) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share media",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Share", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                IconButton(onClick = { onDelete(item) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete media",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6B6B))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePreview(item: MediaItem) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) {
            // Clamp pan offset proportional to zoom level to prevent panning off-screen
            val maxOffset = 500f * (scale - 1f) // Increases with zoom
            Offset(
                x = (offset.x + panChange.x).coerceIn(-maxOffset, maxOffset),
                y = (offset.y + panChange.y).coerceIn(-maxOffset, maxOffset)
            )
        } else {
            Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformableState),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder for media preview — in production this would load actual thumbnails/images
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
        ) {
            Icon(
                imageVector = if (item.type == MediaType.Photo) Icons.Default.Image else Icons.Default.Videocam,
                contentDescription = if (item.type == MediaType.Photo) "Photo preview: ${item.filename}" else "Video preview: ${item.filename}",
                modifier = Modifier.size(120.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.filename,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (item.type == MediaType.Video) {
                item.durationMs?.let { ms ->
                    val totalSeconds = ms / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    Text(
                        text = "%d:%02d".format(minutes, seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataPanel(item: MediaItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp)
    ) {
        MetadataRow("Filename", item.filename)
        MetadataRow("Type", if (item.type == MediaType.Photo) "Photo" else "Video")
        MetadataRow("Dimensions", "${item.width} x ${item.height}")
        MetadataRow("Size", formatFileSize(item.sizeBytes))
        item.durationMs?.let { MetadataRow("Duration", formatDuration(it)) }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

