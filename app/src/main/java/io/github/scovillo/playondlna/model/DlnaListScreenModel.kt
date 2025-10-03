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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.stream.VideoFile
import io.github.scovillo.playondlna.upnpdlna.DlnaDevice
import io.github.scovillo.playondlna.upnpdlna.discoverDlnaDevices
import io.github.scovillo.playondlna.upnpdlna.playUriOnDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DlnaListScreenModel(application: Application) : AndroidViewModel(application) {
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableLiveData("")
    val errorMessage: LiveData<String> = _errorMessage

    fun discoverDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _devices.value = emptyList()
            try {
                val context = getApplication<Application>().applicationContext
                val found = discoverDlnaDevices(context)
                _devices.value = found.filter { it.deviceType.contains("MediaRenderer") }
                _errorMessage.postValue("")
            } catch (exception: Exception) {
                exception.printStackTrace()
                _errorMessage.postValue("Local network scan failed! Is your wifi connected?")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playVideoOnDevice(device: DlnaDevice, videoFile: VideoFile) {
        if (device.avTransportUrl != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    playUriOnDevice(device.avTransportUrl, videoFile)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    _errorMessage.postValue("Playback failed!")
                }
            }
        } else {
            Log.e("DLNA", "No AVTransport URL found.")
            _errorMessage.postValue("Media Player currently incompatible!")
        }
    }

}