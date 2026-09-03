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

package io.github.scovillo.playondlna.dlna.discovery

import android.net.wifi.WifiManager
import android.util.Log
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.DlnaDevice
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.milliseconds

/**
 * SSDP (Simple Service Discovery Protocol) based DLNA device discovery.
 * Uses multicast to search for UPnP devices on the local network.
 */
class SsdpDiscovery(
    private val wifiManager: WifiManager,
    private val detailsClient: SsdpDeviceClient,
) {
    companion object {
        private const val SSDP_GROUP = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val RECEIVE_SOCKET_TIMEOUT_MS = 250
        private const val SEND_ROUNDS = 2
        private const val SEND_ROUND_DELAY_MS = 120L
        private const val QUIET_PERIOD_AFTER_RESPONSE_MS = 900L
    }

    /**
     * Discover DLNA MediaRenderer devices on the network.
     *
     * @param timeoutMs Maximum time to wait for discovery responses
     * @return List of discovered DLNA devices
     */
    suspend fun discoverLocalNetworkMediaRenderers(
        timeoutMs: Long = 5000,
        onDeviceDiscovered: (DlnaDevice) -> Unit = {},
    ): List<DlnaDevice> =
        coroutineScope {
            val lock =
                wifiManager.createMulticastLock("PlayOnDlna:ssdp").apply {
                    setReferenceCounted(true)
                    acquire()
                }
            try {
                ssdpMediaRenderersDiscovery(timeoutMs, onDeviceDiscovered)
            } finally {
                if (lock.isHeld) {
                    lock.release()
                }
            }
        }

    private suspend fun ssdpMediaRenderersDiscovery(
        timeoutMs: Long,
        onDeviceDiscovered: (DlnaDevice) -> Unit,
    ): List<DlnaDevice> =
        coroutineScope {
            val multicastAddress = InetAddress.getByName(SSDP_GROUP)
            val searchTargets =
                listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "ssdp:all",
                )

            val socket =
                DatagramSocket(0).apply {
                    soTimeout = RECEIVE_SOCKET_TIMEOUT_MS
                }

            val seenLocations = mutableSetOf<String>()
            val fetchJobs = mutableListOf<Deferred<Unit>>()
            val discoveredDevices = mutableMapOf<String, DlnaDevice>()

            for (target in searchTargets) {
                val requestBytes = createSsdpRequest(target)
                val packet = DatagramPacket(requestBytes, requestBytes.size, multicastAddress, SSDP_PORT)
                repeat(SEND_ROUNDS) {
                    socket.send(packet)
                    delay(SEND_ROUND_DELAY_MS.milliseconds)
                }
            }

            val startTime = System.currentTimeMillis()
            var lastResponseAt = startTime
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    lastResponseAt = System.currentTimeMillis()

                    val response = buf.decodeToString(0, packet.length)
                    val headers = parseSsdpHeaders(response)

                    val usn = headers["USN"] ?: continue
                    val st = headers["ST"] ?: "unknown"
                    val location = headers["LOCATION"] ?: continue

                    synchronized(seenLocations) {
                        if (location !in seenLocations) {
                            AppLog.i("SSDP", "Device found: $location")
                            seenLocations += location
                            val job =
                                async {
                                    val device = detailsClient.fetch(usn, st, location)
                                    if (device != null && device.deviceType.contains("MediaRenderer")) {
                                        synchronized(discoveredDevices) {
                                            val isNew = discoveredDevices.putIfAbsent(device.location, device) == null
                                            if (isNew) {
                                                onDeviceDiscovered(device)
                                            }
                                        }
                                    }
                                }
                            fetchJobs += job
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    if (
                        seenLocations.isNotEmpty() &&
                        System.currentTimeMillis() - lastResponseAt >= QUIET_PERIOD_AFTER_RESPONSE_MS
                    ) {
                        break
                    }
                }
            }
            socket.close()

            fetchJobs.awaitAll()

            val result = synchronized(discoveredDevices) { discoveredDevices.values.toList() }

            result.forEach {
                Log.d(
                    "SSDP",
                    "⏵ ${it.friendlyName} (${it.modelName}, ${it.deviceType}) @ ${it.location}",
                )
                Log.d("SSDP", it.services.joinToString("\n"))
            }
            result
        }

    private fun createSsdpRequest(st: String): ByteArray {
        val request =
            """
            M-SEARCH * HTTP/1.1
            HOST: $SSDP_GROUP:$SSDP_PORT
            MAN: "ssdp:discover"
            MX: 2
            ST: $st

            """.trimIndent().replace("\n", "\r\n") + "\r\n"
        return request.toByteArray(Charsets.UTF_8)
    }

    private fun parseSsdpHeaders(response: String): Map<String, String> {
        return response
            .lines()
            .drop(1)
            .mapNotNull {
                val idx = it.indexOf(':')
                if (idx != -1) {
                    it.substring(0, idx).trim().uppercase() to
                        it.substring(idx + 1)
                            .trim()
                } else {
                    null
                }
            }
            .toMap()
    }
}
