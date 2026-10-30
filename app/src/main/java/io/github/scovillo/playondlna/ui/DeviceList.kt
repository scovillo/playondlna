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
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.control.PlaybackSession
import io.github.scovillo.playondlna.model.PlaybackScreenModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription

@Composable
fun DlnaListScreen(dlnaModel: PlaybackScreenModel) {
    val selectedDevice by dlnaModel.selectedDevice.collectAsState()
    val sponsorBlockSegmentCounts by dlnaModel.sponsorBlockSegmentCounts.collectAsState()
    val isSponsorBlockEnabled by dlnaModel.isSponsorBlockEnabled.collectAsState()
    val playbackSessions = dlnaModel.playbackSessions
    val deviceId = selectedDevice?.usn
    val session by produceState<PlaybackSession?>(
        initialValue = null,
        key1 = playbackSessions,
        key2 = deviceId,
    ) {
        value = null
        playbackSessions.updates
            .onSubscription {
                value = deviceId?.let { playbackSessions.all[it] }
            }
            .filter { it.id == deviceId }
            .collect { value = it }
    }
    val currentVideo = session?.currentItem
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        dlnaModel.toastEvents.collect { event ->
            if (event is ToastEvent.Show) Toast.makeText(context, context.getString(event.messageResId), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(currentVideo?.metadata?.id, isSponsorBlockEnabled) {
        if (isSponsorBlockEnabled) currentVideo?.metadata?.id?.let(dlnaModel::loadSponsorBlockSegmentCount)
    }
    Column(Modifier.fillMaxSize()) {
        DlnaRemoteControl(
            currentThumbnail = currentVideo?.thumbnail,
            sponsorBlockSegmentCount = if (isSponsorBlockEnabled) currentVideo?.metadata?.id?.let(sponsorBlockSegmentCounts::get) else null,
            session = session,
            onCommand = dlnaModel::remoteControlCommand,
            onSeek = dlnaModel::seekTo
        )
        PlayerList(dlnaModel, Modifier.weight(1f), onSelected = dlnaModel::selectDevice)
    }
}

@Composable
fun PlayerList(
    dlnaModel: PlaybackScreenModel,
    modifier: Modifier = Modifier,
    highlightSelectedPlayer: Boolean = true,
    @StringRes headingRes: Int = R.string.available_players,
    onSelected: (DlnaDevice) -> Unit,
) {
    val devices by dlnaModel.devices.collectAsState()
    val isLoading by dlnaModel.isLoading.collectAsState()
    val favorites by dlnaModel.favoriteDevices.locations.collectAsState()
    val selectedDevice by dlnaModel.selectedDevice.collectAsState()
    LaunchedEffect(Unit) {
        if (dlnaModel.devices.value.isEmpty()) dlnaModel.discoverDevices()
    }
    PlayerListContent(
        devices,
        isLoading,
        favorites,
        if (highlightSelectedPlayer) selectedDevice else null,
        modifier,
        headingRes = headingRes,
        onRefresh = dlnaModel::discoverDevices,
        onFavorite = { device ->
            if (device.location in favorites) {
                dlnaModel.favoriteDevices.removeLocation(device.location)
            } else {
                dlnaModel.favoriteDevices.addLocation(device.location)
            }
        },
        onSelected = onSelected,
    )
}

@Composable
internal fun PlayerListContent(
    devices: List<DlnaDevice>,
    isLoading: Boolean,
    favorites: Set<String>,
    selectedDevice: DlnaDevice?,
    modifier: Modifier = Modifier,
    @StringRes headingRes: Int = R.string.available_players,
    onRefresh: () -> Unit,
    onFavorite: (DlnaDevice) -> Unit,
    onSelected: (DlnaDevice) -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "rotation",
    )
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(headingRes), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh), Modifier.rotate(if (isLoading) rotation else 0f))
            }
        }
        if (devices.isEmpty()) {
            Text(stringResource(if (isLoading) R.string.player_searching else R.string.no_players_found), Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(devices, key = { it.location }) { device ->
                val isSelected = selectedDevice?.location == device.location
                Card(
                    Modifier
                        .animateItem()
                        .padding(8.dp)
                        .fillMaxWidth()
                        .scale(if (isSelected) 1.03f else 1f)
                        .clickable {
                            onSelected(device)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        onFavorite(device)
                                    },
                                ) {
                                    Icon(
                                        imageVector =
                                            if (isFavorite) {
                                                Icons.Filled.Star
                                            } else {
                                                Icons.Outlined.StarBorder
                                            },
                                        contentDescription = stringResource(R.string.favorite),
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Text(
                                    stringResource(R.string.favorite),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
