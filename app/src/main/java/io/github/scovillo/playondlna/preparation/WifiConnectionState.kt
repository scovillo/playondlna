package io.github.scovillo.playondlna.preparation

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class WifiConnectionState(private val connectivityManager: ConnectivityManager) {
    fun isConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}