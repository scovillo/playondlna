package io.github.scovillo.playondlna.ui

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryViewModel
import io.github.scovillo.playondlna.model.PlaylistViewModel
import io.github.scovillo.playondlna.preparation.MediaFileJobStatus
import io.github.scovillo.playondlna.preparation.MediaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    playlistViewModel: PlaylistViewModel,
    mediaModel: MediaModel,
    navController: NavHostController,
    onLibraryChanged: () -> Unit,
    onVideoSelected: () -> Unit,
    onPlayPlaylist: (io.github.scovillo.playondlna.model.Playlist, List<LibraryItem>) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        DownloadPanel(mediaModel)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.library_videos)) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.library_playlists)) })
        }
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                LibraryVideosScreen(libraryViewModel, playlistViewModel, mediaModel, onLibraryChanged, onVideoSelected)
            } else {
                PlaylistsScreen(playlistViewModel, libraryViewModel, mediaModel, navController, onPlayPlaylist)
            }
        }
    }
}

@Composable
private fun DownloadPanel(mediaModel: MediaModel) {
    val progress by mediaModel.progress
    val title by mediaModel.title
    val playlistPosition by mediaModel.playlistPosition
    val status by mediaModel.status
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    var lastPasteAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        mediaModel.toastEvents.collect { event ->
            when (event) {
                is ToastEvent.Show ->
                    Toast.makeText(
                        context,
                        context.getString(event.messageResId),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }
    mediaModel.playlistImportSummary.value?.let { summary ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.playlist_import_summary_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.playlist_import_summary,
                        summary.addedEntries,
                        summary.skippedEntries,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = mediaModel::dismissPlaylistImportSummary) {
                    Text(stringResource(R.string.all_clear))
                }
            },
        )
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        val isDownloadActive = title != "idle" && status != MediaFileJobStatus.ERROR
        Text(
            text = if (title == "idle") stringResource(R.string.src_link) else title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        if (!isDownloadActive) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(all = 4.dp),
                text = stringResource(R.string.or),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPasteAt >= 5_000L) {
                        val url =
                            clipboardManager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                                ?.coerceToText(context)?.toString()?.trim()
                        if (url?.startsWith("http://") == true || url?.startsWith("https://") == true) {
                            lastPasteAt = now
                            mediaModel.import(url)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Text(stringResource(R.string.paste_link_from_clipboard), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (isDownloadActive) {
            playlistPosition?.let { position ->
                Text(
                    text = stringResource(R.string.playlist_download_position, position.current, position.total),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(14.dp),
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
            )
        }
    }
}

@Composable
private fun LibraryVideosScreen(
    libraryViewModel: LibraryViewModel,
    playlistViewModel: PlaylistViewModel,
    mediaModel: MediaModel,
    onLibraryChanged: () -> Unit,
    onVideoSelected: () -> Unit,
) {
    val items by libraryViewModel.items
    val isLoading by libraryViewModel.isLoading
    val isMigrating by libraryViewModel.isMigrating
    val migrationProgress by libraryViewModel.migrationProgress
    val playlists by playlistViewModel.playlists
    var videoToAdd by remember { mutableStateOf<String?>(null) }
    var videoToDelete by remember { mutableStateOf<LibraryItem?>(null) }

    LaunchedEffect(Unit) {
        libraryViewModel.loadLibrary()
        playlistViewModel.loadPlaylists()
        mediaModel.onLibraryChange.collect {
            libraryViewModel.loadLibrary()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMigrating) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(
                    progress = { migrationProgress },
                    modifier =
                        Modifier
                            .width(220.dp)
                            .height(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.library_migration_in_progress))
            }
        } else if (isLoading && items.isEmpty()) {
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
                    val dismissState =
                        rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    videoToDelete = item
                                }
                                false
                            },
                        )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            }
                        },
                    ) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        mediaModel.selectMediaItem(item)
                                        onVideoSelected()
                                    },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ThumbnailImage(file = item.thumbnail, modifier = Modifier.size(100.dp, 70.dp).background(Color.DarkGray))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.metadata.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(item.metadata.uploader, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row {
                                        Text(item.metadata.qualityName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(formatDuration(item.metadata.durationInSeconds), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(formatFileSize(item.sizeInBytes), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { videoToAdd = item.metadata.id }) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    videoToAdd?.let { videoId ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { videoToAdd = null },
            onPlaylistSelected = {
                playlistViewModel.addVideo(it, videoId)
                videoToAdd = null
            },
        )
    }
    videoToDelete?.let { item ->
        val containingPlaylists = playlists.filter { item.metadata.id in it.videoIds }
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text(stringResource(R.string.delete_video_dialog_title)) },
            text = {
                Text(
                    if (containingPlaylists.isEmpty()) {
                        stringResource(R.string.delete_video_dialog_message, item.metadata.title)
                    } else {
                        stringResource(R.string.delete_video_with_playlists_dialog_message, item.metadata.title, containingPlaylists.joinToString { it.name })
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val playlistIds = containingPlaylists.map { it.id }
                    videoToDelete = null
                    libraryViewModel.deleteItem(item, playlistIds) { deleted ->
                        if (deleted) {
                            mediaModel.clearSelectedMediaItem(item.metadata.id)
                            onLibraryChanged()
                        }
                        libraryViewModel.loadLibrary()
                        playlistViewModel.loadPlaylists()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { videoToDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun ThumbnailImage(
    file: File?,
    modifier: Modifier = Modifier,
) {
    val thumbnailFile = file?.takeIf { it.isFile }
    val cacheKey = thumbnailFile?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    var bitmap by remember(cacheKey) { mutableStateOf(cacheKey?.let(thumbnailCache::get)) }

    LaunchedEffect(cacheKey) {
        if (cacheKey != null && bitmap == null) {
            val decoded = withContext(Dispatchers.IO) { runCatching { decodeThumbnail(thumbnailFile!!) }.getOrNull() }
            if (decoded != null) thumbnailCache.put(cacheKey, decoded)
            bitmap = decoded
        }
    }

    when {
        bitmap != null -> Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
        else -> Image(painter = painterResource(R.drawable.playondlna_icon), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

private val thumbnailCache =
    object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(
            key: String,
            value: Bitmap,
        ): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

private fun decodeThumbnail(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 512 || bounds.outHeight / sampleSize > 512) sampleSize *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
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
