package io.github.scovillo.playondlna.model

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.discovery.SsdpDeviceClient
import io.github.scovillo.playondlna.dlna.discovery.SsdpDiscovery
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DeviceDiscoveryModel(application: Application) : AndroidViewModel(application) {
    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    suspend fun discover(onDeviceDiscovered: (DlnaDevice) -> Unit = {}): List<DlnaDevice> {
        return try {
            val context = getApplication<Application>().applicationContext
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val deviceClient = SsdpDeviceClient()
            val discovery = SsdpDiscovery(wifiManager, deviceClient)

            discovery.discoverLocalNetworkMediaRenderers(onDeviceDiscovered = onDeviceDiscovered)
        } catch (e: Exception) {
            _toastEvents.emit(ToastEvent.Show(R.string.multicast_disabled))
            e.printStackTrace()
            emptyList()
        }
    }
}
