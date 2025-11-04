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

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.stream.VideoFile
import io.github.scovillo.playondlna.ui.ToastEvent
import io.github.scovillo.playondlna.upnpdlna.DlnaDevice
import io.github.scovillo.playondlna.upnpdlna.discoverDlnaDevices
import io.github.scovillo.playondlna.upnpdlna.playUriOnDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DlnaListScreenModel(application: Application) : AndroidViewModel(application) {
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    fun discoverDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _devices.value = emptyList()
            try {
                val wifiManager =
                    getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val found = discoverDlnaDevices(wifiManager)
                _devices.value = found.filter { it.deviceType.contains("MediaRenderer") }
            } catch (exception: Exception) {
                exception.printStackTrace()
                _toastEvents.emit(ToastEvent.Show(R.string.multicast_disabled))
            } finally {
                _isLoading.value = false
            }
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