package com.github.scovillo.playondlna.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.scovillo.playondlna.model.DlnaListScreenModel
import com.github.scovillo.playondlna.model.VideoJobModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlnaListScreen(videoJobModel: VideoJobModel, viewModel: DlnaListScreenModel = viewModel()) {
    val devices by viewModel.devices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.discoverDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available Players") },
                actions = {
                    IconButton(onClick = { viewModel.discoverDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    LazyColumn {
                        items(devices) { device ->
                            Card(
                                Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        val videoFile = videoJobModel.currentVideoFileInfo.value
                                        if (videoFile != null) {
                                            viewModel.playVideoOnDevice(device, videoFile)
                                        }
                                    }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        device.friendlyName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        device.modelName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        device.location,
                                        style = MaterialTheme.typography.bodySmall
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
