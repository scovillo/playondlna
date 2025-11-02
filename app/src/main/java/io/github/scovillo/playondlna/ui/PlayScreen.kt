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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.VideoJobModel
import io.github.scovillo.playondlna.model.VideoJobStatus

@Composable
fun PlayScreen(
    videoJobModel: VideoJobModel,
    contentDlnaComposeView: @Composable () -> Unit
) {
    val progress by videoJobModel.progress
    val title by videoJobModel.title
    val status by videoJobModel.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 50.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (title == "idle") stringResource(R.string.src_link) else title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colorResource(id = R.color.white),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = when (status) {
                VideoJobStatus.IDLE -> stringResource(R.string.idle)
                VideoJobStatus.PREPARING -> stringResource(R.string.preparing)
                VideoJobStatus.FINALIZING -> stringResource(R.string.finalizing)
                VideoJobStatus.READY -> stringResource(R.string.ready)
                VideoJobStatus.ERROR -> stringResource(R.string.error)
            },
            modifier = Modifier
                .fillMaxWidth(),
            color = colorResource(id = R.color.white),
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(14.dp),
            color = colorResource(id = R.color.icon_color),
            trackColor = ProgressIndicatorDefaults.linearTrackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            contentDlnaComposeView()
        }
    }
}
