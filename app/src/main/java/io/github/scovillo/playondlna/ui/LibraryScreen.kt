package io.github.scovillo.playondlna.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.LibraryViewModel
import io.github.scovillo.playondlna.model.PlaylistViewModel
import io.github.scovillo.playondlna.preparation.VideoJobModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun libraryScreen(
    libraryViewModel: LibraryViewModel,
    playlistViewModel: PlaylistViewModel,
    videoJobModel: VideoJobModel,
    onVideoSelected: () -> Unit,
) {
    val items by libraryViewModel.items
    val isLoading by libraryViewModel.isLoading
    val playlists by playlistViewModel.playlists
    var videoToAdd by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        libraryViewModel.loadLibrary()
        playlistViewModel.loadPlaylists()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && items.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.no_entries),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable {
                                    videoJobModel.loadFromLibrary(item)
                                    onVideoSelected()
                                },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            asyncThumbnailImage(
                                file = item.thumbnailFile,
                                modifier =
                                    Modifier
                                        .size(100.dp, 70.dp)
                                        .background(Color.DarkGray),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.metadata.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = item.metadata.uploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row {
                                    item.metadata.qualityName?.let { quality ->
                                        Text(
                                            text = quality,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = formatDuration(item.metadata.durationInSeconds),
                                        fontSize = 12.sp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatFileSize(item.sizeInBytes),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            IconButton(onClick = { videoToAdd = item.metadata.id }) {
                                Icon(
                                    Icons.Default.PlaylistAdd,
                                    contentDescription = stringResource(R.string.add_to_playlist),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    videoToAdd?.let { videoId ->
        addToPlaylistDialog(
            playlists = playlists,
            onDismiss = { videoToAdd = null },
            onPlaylistSelected = {
                playlistViewModel.addVideo(it, videoId)
                videoToAdd = null
            },
        )
    }
}

@Composable
fun asyncThumbnailImage(
    file: File?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    bitmap = BitmapFactory.decodeFile(file.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier.background(Color.Gray))
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
