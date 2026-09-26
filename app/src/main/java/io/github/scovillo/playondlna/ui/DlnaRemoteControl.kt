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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackStatus
import io.github.scovillo.playondlna.dlna.control.TransportState
import io.github.scovillo.playondlna.model.LibraryItem
import java.io.File

@Composable
fun DlnaRemoteControl(
    currentVideo: LibraryItem?,
    currentThumbnail: File?,
    playlist: DlnaPlaylist?,
    sponsorBlockSegmentCount: Int?,
    selectedDevice: DlnaDevice?,
    playbackStatus: PlaybackStatus?,
    playlistIndex: Int?,
    playlistSize: Int,
    onCommand: (PlaybackCommand) -> Unit,
    onSeek: (Double) -> Unit,
    onPlay: (DlnaDevice) -> Unit,
    onPlayerRequired: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbnailImage(
                file = currentThumbnail,
                modifier = Modifier.size(96.dp, 64.dp),
            )
            Column(
                modifier =
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
            ) {
                Text(
                    text =
                        currentVideo?.metadata?.title
                            ?: playlist?.title
                            ?: stringResource(R.string.no_media_selected),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                playlist?.let {
                    Text(
                        text = stringResource(R.string.playlist_title, it.title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                selectedDevice?.let {
                    Text(it.friendlyName, style = MaterialTheme.typography.bodyMedium)
                }
                sponsorBlockSegmentCount?.let { count ->
                    Text(
                        text =
                            if (count == 0) {
                                stringResource(R.string.sponsorblock_no_segments)
                            } else {
                                pluralStringResource(R.plurals.sponsorblock_segment_count, count, count)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        val position = playbackStatus?.positionSeconds?.coerceAtLeast(0.0) ?: 0.0
        val duration = playbackStatus?.durationSeconds?.takeIf { it > 0.0 } ?: 0.0
        val isStopped = playbackStatus?.transportState == TransportState.STOPPED
        val isPlaying =
            playbackStatus?.transportState == TransportState.PLAYING ||
                playbackStatus?.transportState == TransportState.TRANSITIONING
        val isPaused = playbackStatus?.transportState == TransportState.PAUSED_PLAYBACK
        val canPlay = currentVideo != null || playlist != null || isPaused
        val canResumePlaylist =
            playbackStatus?.transportState == TransportState.STOPPED && playlistIndex != null
        val hasPrevious = playlistSize > 0 && (playlistIndex ?: 0) > 0
        val hasNext = playlistSize > 0 && (playlistIndex ?: (playlistSize - 1)) < playlistSize - 1
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value =
                        if (duration > 0.0) {
                            position.toFloat().coerceIn(0f, duration.toFloat())
                        } else {
                            0f
                        },
                    onValueChange = { onSeek(it.toDouble()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    enabled = !isStopped && duration > 0.0,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                )
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            RemoteButton(Icons.Default.SkipPrevious, R.string.previous, enabled = hasPrevious) {
                onCommand(PlaybackCommand.PREVIOUS)
            }
            RemoteButton(
                Icons.Default.FastRewind,
                R.string.rewind_30_seconds,
                enabled = isPlaying && duration > 0.0,
            ) {
                onSeek((position - 30.0).coerceAtLeast(0.0))
            }
            RemoteButton(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) R.string.pause else R.string.play,
                enabled = canPlay,
            ) {
                if (isPlaying || isPaused || canResumePlaylist) {
                    onCommand(if (isPlaying) PlaybackCommand.PAUSE else PlaybackCommand.PLAY)
                } else {
                    selectedDevice?.let(onPlay) ?: onPlayerRequired()
                }
            }
            RemoteButton(Icons.Default.Stop, R.string.stop, enabled = isPlaying) {
                onCommand(PlaybackCommand.STOP)
            }
            RemoteButton(
                Icons.Default.FastForward,
                R.string.forward_30_seconds,
                enabled = isPlaying && duration > 0.0,
            ) {
                onSeek((position + 30.0).coerceAtMost(duration))
            }
            RemoteButton(Icons.Default.SkipNext, R.string.next, enabled = hasNext) {
                onCommand(PlaybackCommand.NEXT)
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun RemoteButton(
    icon: ImageVector,
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(36.dp))
    }
}
