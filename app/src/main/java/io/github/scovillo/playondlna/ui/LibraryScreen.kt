package io.github.scovillo.playondlna.ui

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryViewModel
import io.github.scovillo.playondlna.model.PlaylistViewModel
import io.github.scovillo.playondlna.preparation.MediaFileJobStatus
import io.github.scovillo.playondlna.preparation.VideoJobModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    playlistViewModel: PlaylistViewModel,
    videoJobModel: VideoJobModel,
    navController: NavHostController,
    onVideoSelected: () -> Unit,
    onPlayPlaylist: (io.github.scovillo.playondlna.model.Playlist, List<LibraryItem>) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        DownloadPanel(videoJobModel)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.library_videos)) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.library_playlists)) })
        }
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                LibraryVideosScreen(libraryViewModel, playlistViewModel, videoJobModel, onVideoSelected)
            } else {
                PlaylistsScreen(playlistViewModel, libraryViewModel, videoJobModel, navController, onPlayPlaylist)
            }
        }
    }
}

@Composable
private fun DownloadPanel(videoJobModel: VideoJobModel) {
    val progress by videoJobModel.progress
    val title by videoJobModel.title
    val playlistPosition by videoJobModel.playlistPosition
    val status by videoJobModel.status
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    var lastPasteAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        videoJobModel.toastEvents.collect { event ->
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
    videoJobModel.playlistImportSummary.value?.let { summary ->
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
                TextButton(onClick = videoJobModel::dismissPlaylistImportSummary) {
                    Text(stringResource(R.string.all_clear))
                }
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
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
                modifier = Modifier
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
                            videoJobModel.prepareVideo(url)
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
                modifier = Modifier
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
    videoJobModel: VideoJobModel,
    onVideoSelected: () -> Unit,
) {
    val items by libraryViewModel.items
    val isLoading by libraryViewModel.isLoading
    val playlists by playlistViewModel.playlists
    val completedVideo by videoJobModel.currentVideoFile
    var videoToAdd by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        libraryViewModel.loadLibrary()
        playlistViewModel.loadPlaylists()
    }
    LaunchedEffect(completedVideo?.metadata?.id) {
        if (completedVideo != null) libraryViewModel.loadLibrary()
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
                                    videoJobModel.selectMediaItem(item)
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
                            ThumbnailImage(
                                file = item.thumbnail,
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
                                    item.metadata.qualityName.let { quality ->
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
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
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
        AddToPlaylistDialog(
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
fun ThumbnailImage(
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
        Image(
            painter = painterResource(R.drawable.playondlna_icon),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
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
