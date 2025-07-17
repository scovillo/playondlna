package com.github.scovillo.playondlna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.scovillo.playondlna.model.VideoJobModel
import com.github.scovillo.playondlna.model.VideoJobStatus
import de.scovillo.playondlna.R

@Composable
fun MainScreen(
    videoJobModel: VideoJobModel = viewModel(),
    onClearCache: () -> Unit,
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
        Box(Modifier.padding(16.dp)) {
            Button(
                onClick = onClearCache,
                modifier = Modifier.padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.icon_color),
                    contentColor = colorResource(id = R.color.white)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.clear_cache),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
