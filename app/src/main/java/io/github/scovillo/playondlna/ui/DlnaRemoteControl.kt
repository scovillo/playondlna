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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.observation.PlaybackSession
import io.github.scovillo.playondlna.dlna.transport.PlaybackCommand
import io.github.scovillo.playondlna.dlna.transport.TransportState
import java.io.File

@Composable
fun DlnaRemoteControl(
    currentThumbnail: File?,
    sponsorBlockSegmentCount: Int?,
    session: PlaybackSession?,
    onCommand: (PlaybackCommand) -> Unit,
    onSeek: (Double) -> Unit,
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
                        session?.takeUnless { it.isExternalPlayback }?.currentItem?.metadata?.title
                            ?: stringResource(
                                when {
                                    session?.device == null -> R.string.select_player_for_playback
                                    session.currentState == TransportState.PLAYING -> R.string.playback_running
                                    session.currentState == TransportState.PAUSED_PLAYBACK -> R.string.playback_paused
                                    session.currentState == TransportState.TRANSITIONING -> R.string.playback_loading
                                    session.currentState in setOf(TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT) -> R.string.playback_idle
                                    else -> R.string.playback_status_unknown
                                },
                            ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val displayPlaylist = session?.takeUnless { it.isExternalPlayback }?.playlist
                displayPlaylist?.items?.size?.let {
                    if (it > 1) {
                        Text(
                            text = stringResource(R.string.playlist_title, displayPlaylist.name),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (session?.device != null) {
                    Text(session.device.friendlyName, style = MaterialTheme.typography.bodyMedium)
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
        val position = session?.currentPosition?.coerceAtLeast(0.0) ?: 0.0
        val duration = session?.positionInfo?.durationSeconds?.takeIf { it > 0.0 } ?: 0.0
        val isPlaying =
            session?.currentState == TransportState.PLAYING ||
                session?.currentState == TransportState.TRANSITIONING
        val isPaused = session?.currentState == TransportState.PAUSED_PLAYBACK
        val canSeek =
            (session?.currentState == TransportState.PLAYING || isPaused) && duration > 0.0
        var seekPreview by remember(session?.device?.usn, session?.positionInfo?.trackUri, canSeek) { mutableStateOf<Float?>(null) }
        val displayedPosition = seekPreview?.toDouble() ?: position
        val canPlay = session != null
        val hasPrevious = session?.hasPrevious ?: false
        val hasNext = session?.hasNext ?: false
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(displayedPosition), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value =
                        if (duration > 0.0) {
                            displayedPosition.toFloat().coerceIn(0f, duration.toFloat())
                        } else {
                            0f
                        },
                    onValueChange = { seekPreview = it },
                    onValueChangeFinished = {
                        val target = seekPreview
                        seekPreview = null
                        if (canSeek && target != null) onSeek(target.toDouble())
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    enabled = canSeek,
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
                enabled = canSeek,
            ) {
                onSeek((position - 30.0).coerceAtLeast(0.0))
            }
            RemoteButton(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) R.string.pause else R.string.play,
                enabled = canPlay,
            ) {
                onCommand(if (isPlaying) PlaybackCommand.PAUSE else PlaybackCommand.PLAY)
            }
            RemoteButton(Icons.Default.Stop, R.string.stop, enabled = isPlaying || isPaused) {
                onCommand(PlaybackCommand.STOP)
            }
            RemoteButton(
                Icons.Default.FastForward,
                R.string.forward_30_seconds,
                enabled = canSeek,
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
