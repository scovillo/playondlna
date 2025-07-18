package com.github.scovillo.playondlna.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.scovillo.playondlna.stream.VideoFileInfo
import com.github.scovillo.playondlna.upnpdlna.DlnaDevice
import com.github.scovillo.playondlna.upnpdlna.discoverDlnaDevices
import com.github.scovillo.playondlna.upnpdlna.playUriOnDevice
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