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
import io.github.scovillo.playondlna.server.VideoFile
import io.github.scovillo.playondlna.ui.ToastEvent
import io.github.scovillo.playondlna.upnpdlna.DlnaDevice
import io.github.scovillo.playondlna.upnpdlna.FavoriteDevices
import io.github.scovillo.playondlna.upnpdlna.SsdpDevices
import io.github.scovillo.playondlna.upnpdlna.playUriOnDevice
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
    private val ssdpDevices: SsdpDevices,
    val favoriteDevices: FavoriteDevices
) : ViewModel() {
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = merge(_toastEvents.asSharedFlow(), ssdpDevices.toastEvents)

    fun discoverDevices() {
        viewModelScope.launch {
            _isLoading.value = true
            _devices.value = emptyList()
            val jobs = listOf(
                launch(Dispatchers.IO) {
                    val ssdp = ssdpDevices.discover()
                    _devices.update { it + ssdp }
                },
                launch(Dispatchers.IO) {
                    val manual = favoriteDevices.discover()
                    _devices.update { it + manual }
                }
            )
            jobs.joinAll()
            _devices.update { it.distinctBy { device -> device.location } }
            _isLoading.value = false
        }
    }

    fun playVideoOnDevice(device: DlnaDevice, videoFile: VideoFile) {
        viewModelScope.launch(Dispatchers.IO) {
            if (device.avTransportUrl != null) {
                try {
                    Log.d("playVideoOnDevice", "Send playback command to ${device.avTransportUrl}")
                    playUriOnDevice(device.avTransportUrl, videoFile)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    _toastEvents.emit(ToastEvent.Show(R.string.playback_failed))
                }
            } else {
                Log.e(
                    "playVideoOnDevice",
                    "No AVTransport URL found for ${device.friendlyName} @ ${device.location}"
                )
                _toastEvents.emit(ToastEvent.Show(R.string.player_incompatible))
            }
        }
    }
}