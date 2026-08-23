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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.server.VideoFile
import io.github.scovillo.playondlna.upnpdlna.DlnaDevice
import io.github.scovillo.playondlna.upnpdlna.DlnaMedia
import io.github.scovillo.playondlna.upnpdlna.TransportCommand
import java.io.File

@Composable
fun dlnaRemoteControl(
    currentVideo: VideoFile?,
    currentThumbnail: File?,
    playlistMedia: DlnaMedia?,
    selectedDevice: DlnaDevice?,
    onCommand: (TransportCommand) -> Unit,
    onPlay: (DlnaDevice) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            asyncThumbnailImage(
                file = currentThumbnail,
                modifier = Modifier.size(96.dp, 64.dp),
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = playlistMedia?.title ?: currentVideo?.title ?: stringResource(R.string.no_media_selected),
                    style = MaterialTheme.typography.titleLarge,
                )
                selectedDevice?.let {
                    Text(it.friendlyName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            remoteButton(Icons.Default.SkipPrevious, R.string.previous) {
                onCommand(TransportCommand.PREVIOUS)
            }
            remoteButton(Icons.Default.PlayArrow, R.string.play) {
                selectedDevice?.let(onPlay) ?: onCommand(TransportCommand.PLAY)
            }
            remoteButton(Icons.Default.Pause, R.string.pause) {
                onCommand(TransportCommand.PAUSE)
            }
            remoteButton(Icons.Default.Stop, R.string.stop) {
                onCommand(TransportCommand.STOP)
            }
            remoteButton(Icons.Default.SkipNext, R.string.next) {
                onCommand(TransportCommand.NEXT)
            }
        }
    }
}

@Composable
private fun remoteButton(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(36.dp))
    }
}
