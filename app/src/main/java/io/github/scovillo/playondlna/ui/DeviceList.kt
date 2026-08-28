/*
 * PlayOnDlna - An Android application to play media on dlna devices
 * Copyright (C) 2025 Lukas Scheerer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.scovillo.playondlna.ui

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.model.DlnaDevicesListScreenModel
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.preparation.MediaModel

@Composable
fun DlnaListScreen(
    mediaModel: MediaModel,
    dlnaModel: DlnaDevicesListScreenModel,
    playlistVideoFiles: List<LibraryItem> = emptyList(),
    playlist: DlnaPlaylist? = null,
) {
    val devices by dlnaModel.devices.collectAsState()
    val isLoading by dlnaModel.isLoading.collectAsState()
    val favorites by dlnaModel.favoriteDevices.locations.collectAsState()
    val selectedDevice by dlnaModel.selectedDevice.collectAsState()
    LaunchedEffect(Unit) {
        if (dlnaModel.devices.value.isEmpty()) {
            dlnaModel.discoverDevices()
        }
    }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        dlnaModel.toastEvents.collect { event ->
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
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )
    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column {
                val currentVideo = mediaModel.currentVideoFile.value
                val currentThumbnail = mediaModel.currentVideoFile.value?.thumbnail
                DlnaRemoteControl(
                    currentVideo = currentVideo,
                    currentThumbnail = currentThumbnail,
                    playlist = playlist,
                    selectedDevice = selectedDevice,
                    onCommand = dlnaModel::remoteCommand,
                    onPlay = { device ->
                        if (playlistVideoFiles.isNotEmpty() && playlist != null) {
                            dlnaModel.playPlaylistOnDevice(device, playlist, playlistVideoFiles)
                        } else if (currentVideo != null) {
                            dlnaModel.playVideoOnDevice(device, currentVideo)
                        } else {
                            dlnaModel.remoteCommand(PlaybackCommand.PLAY)
                        }
                    },
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.available_players),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = { dlnaModel.discoverDevices() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.rotate(if (isLoading) rotation else 0f),
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        val isSelected = selectedDevice?.location == device.location
                        Card(
                            Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                                .scale(if (isSelected) 1.03f else 1f)
                                .clickable {
                                    dlnaModel.selectDevice(device)
                                },
                            colors =
                                androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = if (isSelected) colorResource(R.color.icon_color) else MaterialTheme.colorScheme.surface,
                                ),
                        ) {
                            val isFavorite = device.location in favorites
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        device.friendlyName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        device.modelName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        device.location,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        if (isFavorite) {
                                            dlnaModel.favoriteDevices.removeLocation(device.location)
                                        } else {
                                            dlnaModel.favoriteDevices.addLocation(device.location)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector =
                                            if (isFavorite) {
                                                Icons.Filled.Star
                                            } else {
                                                Icons.Outlined.StarBorder
                                            },
                                        contentDescription = stringResource(R.string.save_device),
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
