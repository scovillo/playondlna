package io.github.scovillo.playondlna.upnpdlna

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SsdpDevices(application: Application) : AndroidViewModel(application) {
    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    suspend fun discover(): List<DlnaDevice> {
        return try {
            val wifiManager =
                getApplication<Application>()
                    .applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
            return discoverDlnaDevices(wifiManager)
                .filter { it.deviceType.contains("MediaRenderer") }
        } catch (e: Exception) {
            _toastEvents.emit(ToastEvent.Show(R.string.multicast_disabled))
            e.printStackTrace()
            emptyList()
        }
    }
}