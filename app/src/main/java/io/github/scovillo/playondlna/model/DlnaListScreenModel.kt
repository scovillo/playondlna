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
import io.github.scovillo.playondlna.stream.VideoFileInfo
import io.github.scovillo.playondlna.upnpdlna.DlnaDevice
import io.github.scovillo.playondlna.upnpdlna.discoverDlnaDevices
import io.github.scovillo.playondlna.upnpdlna.playUriOnDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DlnaListScreenModel : ViewModel() {
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun discoverDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _devices.value = emptyList()
            val found = discoverDlnaDevices()
            _devices.value = found.filter { it.deviceType.contains("MediaRenderer") }
            _isLoading.value = false
        }
    }

    fun playVideoOnDevice(device: DlnaDevice, videoFileInfo: VideoFileInfo) {
        if (device.avTransportUrl != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    playUriOnDevice(device.avTransportUrl, videoFileInfo)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
            }
        } else {
            Log.e("DLNA", "No AVTransport URL found.")
        }
    }

}