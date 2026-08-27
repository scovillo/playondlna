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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.server.VideoFile
import io.github.scovillo.playondlna.ui.ToastEvent
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.DlnaMedia
import io.github.scovillo.playondlna.dlna.FavoriteDevices
import io.github.scovillo.playondlna.dlna.DeviceDiscoveryModel
import io.github.scovillo.playondlna.dlna.control.DlnaRemoteControl
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
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

class DlnaDevicesListScreenModel(
    private val deviceDiscoveryModel: DeviceDiscoveryModel,
    val favoriteDevices: FavoriteDevices,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val remote =
        DlnaRemoteControl(
            scope = viewModelScope,
            nativePlaylistSupport = settingsRepository::nativePlaylistSupport,
            saveNativePlaylistSupport = settingsRepository::saveNativePlaylistSupport,
            onIncompatibleDevice = ::incompatibleDevice,
            onPlaybackFailure = { _toastEvents.emit(ToastEvent.Show(R.string.playback_failed)) },
        )

    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = merge(_toastEvents.asSharedFlow(), deviceDiscoveryModel.toastEvents)

    fun discoverDevices() {
        viewModelScope.launch {
            _isLoading.value = true
            _devices.value = emptyList()
            val jobs =
                listOf(
                    launch(Dispatchers.IO) {
                        val ssdp = deviceDiscoveryModel.discover()
                        _devices.update { it + ssdp }
                    },
                    launch(Dispatchers.IO) {
                        val manual = favoriteDevices.discover()
                        _devices.update { it + manual }
                    },
                )
            jobs.joinAll()
            _devices.update { it.distinctBy { device -> device.location } }
            _isLoading.value = false
        }
    }

    fun selectDevice(device: DlnaDevice) {
        _selectedDevice.value = device
    }

    fun playVideoOnDevice(
        device: DlnaDevice,
        videoFile: VideoFile,
    ) = remote.playVideo(device, videoFile)

    fun playPlaylistOnDevice(
        device: DlnaDevice,
        nativePlaylist: DlnaMedia,
        videoFiles: List<VideoFile>,
    ) = remote.playPlaylist(device, nativePlaylist, videoFiles)

    fun remoteCommand(command: PlaybackCommand) {
        _selectedDevice.value?.let { remote.command(it, command) }
    }

    private suspend fun incompatibleDevice(device: DlnaDevice) {
        Log.e(
            "playVideoOnDevice",
            "No AVTransport URL found for ${device.friendlyName} @ ${device.location}",
        )
        _toastEvents.emit(ToastEvent.Show(R.string.player_incompatible))
    }
}
