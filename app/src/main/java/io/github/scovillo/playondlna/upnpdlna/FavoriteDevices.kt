package io.github.scovillo.playondlna.upnpdlna

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URL

class FavoriteDevices(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    val locations: StateFlow<Set<String>> =
        settingsRepository.favoriteDeviceLocationsFlow
            .map { it.toSet() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptySet()
            )

    fun discoverLocation(url: URL) {
        viewModelScope.launch(Dispatchers.IO) {
            val location = url.toString()
            try {
                val device = fetchDeviceDescription(
                    usn = "manual-$location",
                    st = "manual",
                    location = location
                )
                if (device != null) {
                    if (device.deviceType.contains("MediaRenderer")) {
                        AppLog.i("FavoriteDevices", device.toString())
                        addLocation(location)
                    } else {
                        AppLog.w("FavoriteDevices", "$location is not a MediaRenderer")
                        _toastEvents.emit(ToastEvent.Show(R.string.location_not_renderer))
                    }
                } else {
                    AppLog.w("FavoriteDevices", "$location not reachable")
                    _toastEvents.emit(ToastEvent.Show(R.string.location_not_reachable))
                }
            } catch (e: Exception) {
                AppLog.w("FavoriteDevices", "$location not reachable")
                e.printStackTrace()
            }
        }
    }

    fun addLocation(location: String) {
        AppLog.i("FavoriteDevices", "Add $location")
        viewModelScope.launch {
            settingsRepository.saveFavoriteDeviceLocation(location)
        }
    }

    fun removeLocation(location: String) {
        AppLog.i("FavoriteDevices", "Remove $location")
        viewModelScope.launch {
            settingsRepository.removeFavoriteLocation(location)
        }
    }

    suspend fun discover(): List<DlnaDevice> {
        val locations = locations.first()
        return locations.mapNotNull { location ->
            try {
                fetchDeviceDescription(
                    usn = "manual-$location",
                    st = "manual",
                    location = location
                )
            } catch (_: Exception) {
                AppLog.w("loadManualDevices", "$location not reachable")
                null
            }
        }
    }
}