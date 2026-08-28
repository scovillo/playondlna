package io.github.scovillo.playondlna.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryViewModel
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.model.PlaylistViewModel
import io.github.scovillo.playondlna.preparation.MediaModel

@Composable
fun PlaylistsScreen(
    playlistViewModel: PlaylistViewModel,
    libraryViewModel: LibraryViewModel,
    mediaModel: MediaModel,
    navController: NavHostController,
    onPlayPlaylist: (Playlist, List<LibraryItem>) -> Unit,
) {
    val playlists by playlistViewModel.playlists
    val libraryItems by libraryViewModel.items
    val playlistId = navController.currentBackStackEntry?.arguments?.getString("playlistId")
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(Unit) {
        playlistViewModel.loadPlaylists()
        libraryViewModel.loadLibrary()
    }
    val playlist = playlists.find { it.id == playlistId }
    if (playlistId != null && playlist != null) {
        PlaylistDetails(
            playlist = playlist,
            libraryViewModel = libraryViewModel,
            mediaModel = mediaModel,
            onRemove = { playlistViewModel.removeVideo(playlist.id, it) },
            onPlay = onPlayPlaylist,
        )
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            Button(onClick = { creating = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.create_playlist), modifier = Modifier.padding(start = 8.dp))
            }
            if (playlists.isEmpty()) {
                Text(
                    stringResource(R.string.no_playlists),
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 32.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlists, key = { it.id }) { item ->
                        val playableItems = item.videoIds.mapNotNull { videoId -> libraryItems.find { it.metadata.id == videoId } }
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { navController.navigate("playlist/${item.id}") },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.playlist_video_count, item.videoIds.size), style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = { onPlayPlaylist(item, playableItems) },
                                    enabled = playableItems.isNotEmpty(),
                                ) {
                                    Icon(Icons.Default.PlayArrow, stringResource(R.string.play_playlist))
                                }
                                IconButton(onClick = { renaming = item }) { Icon(Icons.Default.Edit, stringResource(R.string.rename_playlist)) }
                                IconButton(onClick = { deleting = item }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_playlist)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        PlaylistNameDialog(R.string.create_playlist, "", { creating = false }) {
            playlistViewModel.createPlaylist(it)
            creating = false
        }
    }
    renaming?.let { target ->
        PlaylistNameDialog(R.string.rename_playlist, target.name, { renaming = null }) {
            playlistViewModel.renamePlaylist(target.id, it)
            renaming = null
        }
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_message, target.name)) },
            confirmButton = {
                Button(onClick = {
                    playlistViewModel.deletePlaylist(target.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { Button(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PlaylistDetails(
    playlist: Playlist,
    libraryViewModel: LibraryViewModel,
    mediaModel: MediaModel,
    onRemove: (String) -> Unit,
    onPlay: (Playlist, List<LibraryItem>) -> Unit,
) {
    val itemById = libraryViewModel.items.value.associateBy { it.metadata.id }
    val missingCount = playlist.videoIds.count { it !in itemById }
    val validItems = playlist.videoIds.mapNotNull(itemById::get)
    var startError by remember(playlist.id) { mutableStateOf(false) }
    var isStarting by remember(playlist.id) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(playlist.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (validItems.isEmpty()) {
                    startError = true
                } else if (!isStarting) {
                    isStarting = true
                    onPlay(playlist, validItems)
                }
            }, enabled = !isStarting) {
                Icon(Icons.Default.PlayArrow, stringResource(R.string.play_playlist))
            }
        }
        if (missingCount > 0) Text(stringResource(R.string.playlist_missing_videos, missingCount), style = MaterialTheme.typography.bodySmall)
        if (startError) Text(stringResource(R.string.playlist_no_playable_videos), style = MaterialTheme.typography.bodyMedium)
        if (validItems.isEmpty()) {
            Text(stringResource(R.string.no_playlist_videos), modifier = Modifier.padding(top = 24.dp))
        } else {
            LazyColumn {
                items(validItems, key = { it.metadata.id }) { item ->
                    val videoId = item.metadata.id
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { mediaModel.selectMediaItem(item) },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.metadata.title)
                                Text(item.metadata.uploader, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onRemove(videoId) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_from_playlist)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: Int,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(title))
    }, text = {
        OutlinedTextField(name, {
            name = it
        }, label = {
            Text(stringResource(R.string.playlist_name))
        })
    }, confirmButton = {
        Button(onClick = {
            onConfirm(name)
        }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
    }, dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_to_playlist)) }, text = {
        if (playlists.isEmpty()) {
            Text(stringResource(R.string.no_playlists_to_add))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                playlists.forEach { playlist ->
                    Button(onClick = { onPlaylistSelected(playlist.id) }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
