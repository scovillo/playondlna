package de.scovillo.playondlna.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.scovillo.playondlna.upnp.DlnaDeviceDescription
import de.scovillo.playondlna.upnp.discoverDlnaDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DlnaViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<DlnaDeviceDescription>>(emptyList())
    val devices: StateFlow<List<DlnaDeviceDescription>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun discoverDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _devices.value = emptyList()
            val found = discoverDlnaDevices()
            _devices.value = found
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlnaListScreen(viewModel: DlnaViewModel = viewModel()) {
    val devices by viewModel.devices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedDevice by remember { mutableStateOf<DlnaDeviceDescription?>(null) }

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
                }
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

                selectedDevice != null -> {
                    DlnaDeviceDetail(device = selectedDevice!!) {
                        selectedDevice = null
                    }
                }

                else -> {
                    LazyColumn {
                        items(devices) { device ->
                            Card(
                                Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                                    .clickable { selectedDevice = device }
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
