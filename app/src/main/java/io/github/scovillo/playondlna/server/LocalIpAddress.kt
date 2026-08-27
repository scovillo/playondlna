package io.github.scovillo.playondlna.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

class LocalIpAddress {
    private val cacheLock = Any()

    @Volatile
    private var cachedAddress: String? = null

    fun value(): String? =
        cachedAddress ?: synchronized(cacheLock) {
            cachedAddress ?: resolve()?.also { cachedAddress = it }
        }

    private fun resolve(): String? =
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
}

val localIpAddress = LocalIpAddress()
