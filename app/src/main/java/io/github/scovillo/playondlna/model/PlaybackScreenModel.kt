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

package io.github.scovillo.playondlna.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.FavoriteDevices
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackSession
import io.github.scovillo.playondlna.dlna.control.PlaybackSessions
import io.github.scovillo.playondlna.dlna.control.SoapDlnaTransport
import io.github.scovillo.playondlna.dlna.control.SponsorBlockClient
import io.github.scovillo.playondlna.dlna.control.TransportState
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.server.LocalIpAddress
import io.github.scovillo.playondlna.server.MediaFileResource
import io.github.scovillo.playondlna.server.ServerPort
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class PlaybackScreenModel(
    private val deviceDiscoveryModel: DeviceDiscoveryModel,
    private val localIpAddress: LocalIpAddress,
    private val serverPort: ServerPort,
    val favoriteDevices: FavoriteDevices,
    val playbackSessions: PlaybackSessions,
    private val settingsRepository: SettingsRepository,
    private val sponsorBlockClient: SponsorBlockClient,
    private val onPlaybackRequested: () -> Unit = {},
) : ViewModel() {
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSponsorBlockEnabled = MutableStateFlow(false)
    val isSponsorBlockEnabled: StateFlow<Boolean> = _isSponsorBlockEnabled.asStateFlow()
    private val _sponsorBlockSegmentCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sponsorBlockSegmentCounts: StateFlow<Map<String, Int>> = _sponsorBlockSegmentCounts.asStateFlow()

    private val dlnaClient = SoapDlnaTransport()

    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = merge(_toastEvents.asSharedFlow(), deviceDiscoveryModel.toastEvents)

    init {
        viewModelScope.launch {
            favoriteDevices.locations.collect { favorites ->
                _devices.update { current -> sortDevices(current, favorites) }
            }
        }
        viewModelScope.launch {
            settingsRepository.isSponsorBlockEnabledFlow.collect { _isSponsorBlockEnabled.value = it }
        }
    }

    fun discoverDevices() {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            _devices.value = emptyList()
            val jobs =
                listOf(
                    launch(Dispatchers.IO) {
                        val ssdp = deviceDiscoveryModel.discover(onDeviceDiscovered = ::addDevice)
                        ssdp.forEach(::addDevice)
                    },
                    launch(Dispatchers.IO) {
                        val manual = favoriteDevices.discover()
                        manual.forEach(::addDevice)
                    },
                )
            jobs.joinAll()
            _isLoading.value = false
        }
    }

    private fun addDevice(device: DlnaDevice) {
        val favorites = favoriteDevices.locations.value
        _devices.update { current ->
            if (current.any { it.location == device.location }) {
                current
            } else {
                sortDevices(current + device, favorites)
            }
        }
    }

    private fun sortDevices(
        devices: List<DlnaDevice>,
        favorites: Set<String>,
    ): List<DlnaDevice> {
        return devices.sortedWith(
            compareByDescending<DlnaDevice> { it.location in favorites }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.friendlyName }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.location },
        )
    }

    fun selectDevice(device: DlnaDevice) {
        _selectedDevice.value = device
        if (!playbackSessions.has(device.usn)) {
            playbackSessions.register(PlaybackSession.init(device, Playlist.empty(), TransportState.UNKNOWN))
        }
    }

    fun startOnDevice(
        device: DlnaDevice,
        playlist: Playlist,
    ) {
        onPlaybackRequested()
        if (!playbackSessions.has(device.usn)) {
            playbackSessions.register(PlaybackSession.init(device, playlist, TransportState.UNKNOWN))
        } else {
            playbackSessions.update(device.usn, playlist)
        }
        val session = playbackSessions.get(device.usn)
        viewModelScope.launch(Dispatchers.IO) {
            dlnaClient.playFile(
                device,
                MediaFileResource(localIpAddress, serverPort, session.currentItem!!),
            )
        }
    }

    fun startOnDevice(
        device: DlnaDevice,
        item: LibraryItem,
    ) = startOnDevice(device, Playlist("", item.metadata.title, listOf(item)))

    fun loadSponsorBlockSegmentCount(item: LibraryItem) {
        if (item.metadata.source.isNotBlank() && !item.metadata.source.equals("YouTube", ignoreCase = true)) return
        val videoId = item.metadata.id
        if (!isSponsorBlockEnabled.value || _sponsorBlockSegmentCounts.value.containsKey(videoId)) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!isSponsorBlockEnabled.value) return@launch
            val segmentCount =
                runCatching { sponsorBlockClient.fetchSegments(videoId).size }.getOrNull()
                    ?: return@launch
            _sponsorBlockSegmentCounts.update { it + (videoId to segmentCount) }
        }
    }

    fun remoteControlCommand(command: PlaybackCommand) {
        val device = _selectedDevice.value ?: return
        when (command) {
            PlaybackCommand.NEXT, PlaybackCommand.PREVIOUS -> {
                val session = playbackSessions.get(device.usn)
                val nextIndex = playbackSessions.get(device.usn).predictIndex(command)
                viewModelScope.launch(Dispatchers.IO) {
                    dlnaClient.playFile(device, MediaFileResource(localIpAddress, serverPort, session.playlist[nextIndex]))
                }
            }

            else -> {
                viewModelScope.launch(Dispatchers.IO) {
                    dlnaClient.command(device, command)
                }
            }
        }
    }

    fun notifyPlayerRequired() {
        viewModelScope.launch {
            _toastEvents.emit(ToastEvent.Show(R.string.select_player_for_playback))
        }
    }

    fun seekTo(seconds: Double) {
        _selectedDevice.value?.let {
            viewModelScope.launch(Dispatchers.IO) { dlnaClient.seekTo(it, seconds) }
        }
    }
}
