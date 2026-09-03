package io.github.scovillo.playondlna.server

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

class LocalIpAddress {
    private val wifiAddressesLock = Any()
    private val wifiAddresses = linkedMapOf<Network, Inet4Address>()

    @Volatile
    private var connectivityManager: ConnectivityManager? = null

    fun initialize(connectivityManager: ConnectivityManager) {
        synchronized(wifiAddressesLock) {
            if (this.connectivityManager != null) return
            this.connectivityManager = connectivityManager
        }
        connectivityManager.activeNetwork
            ?.takeIf { network ->
                connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                } == true
            }?.let { network ->
                connectivityManager.getLinkProperties(network)?.ipv4Address()?.let { address ->
                    synchronized(wifiAddressesLock) {
                        wifiAddresses[network] = address
                    }
                }
            }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            networkCallback,
        )
    }

    fun value(): String? = resolveWifi()?.address?.hostAddress ?: resolveFallback()

    fun wifiNetwork(): Network? = resolveWifi()?.network

    private fun resolveWifi(): WifiAddress? {
        val manager = connectivityManager ?: return null
        val activeNetwork = manager.activeNetwork
        return synchronized(wifiAddressesLock) {
            val network =
                activeNetwork?.takeIf(wifiAddresses::containsKey)
                    ?: wifiAddresses.keys.firstOrNull()
                    ?: return@synchronized null
            WifiAddress(network, wifiAddresses.getValue(network))
        }
    }

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) {
                val address = linkProperties.ipv4Address()
                synchronized(wifiAddressesLock) {
                    if (address == null) {
                        wifiAddresses.remove(network)
                    } else {
                        wifiAddresses[network] = address
                    }
                }
            }

            override fun onLost(network: Network) {
                synchronized(wifiAddressesLock) {
                    wifiAddresses.remove(network)
                }
            }
        }

    private fun resolveFallback(): String? =
        try {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: SocketException) {
            null
        }

    private data class WifiAddress(
        val network: Network,
        val address: Inet4Address,
    )
}

private fun LinkProperties.ipv4Address(): Inet4Address? =
    linkAddresses
        .asSequence()
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }

val localIpAddress = LocalIpAddress()
