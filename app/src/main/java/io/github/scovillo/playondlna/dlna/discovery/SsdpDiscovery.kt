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

/**
 * SSDP (Simple Service Discovery Protocol) based DLNA device discovery.
 * Uses multicast to search for UPnP devices on the local network.
 */
class SsdpDiscovery(
    private val wifiManager: WifiManager,
    private val detailsClient: SsdpDeviceClient,
) {
    /**
     * Discover DLNA MediaRenderer devices on the network.
     *
     * @param timeoutMs Maximum time to wait for discovery responses
     * @return List of discovered DLNA devices
     */
    suspend fun discoverLocalNetworkMediaRenderers(timeoutMs: Long = 5000): List<DlnaDevice> =
        coroutineScope {
            val lock =
                wifiManager.createMulticastLock("PlayOnDlna:ssdp").apply {
                    setReferenceCounted(true)
                    acquire()
                }
            try {
                ssdpMediaRenderersDiscovery(timeoutMs)
            } finally {
                if (lock.isHeld) {
                    lock.release()
                }
            }
        }

    private suspend fun ssdpMediaRenderersDiscovery(timeoutMs: Long): List<DlnaDevice> =
        coroutineScope {
            val multicastAddress = InetAddress.getByName("239.255.255.250")
            val searchTargets =
                listOf(
                    "ssdp:all",
                    "upnp:rootdevice",
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:device:MediaServer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                )

            val socket =
                DatagramSocket(0).apply {
                    soTimeout = 1000
                }

            val seenLocations = mutableSetOf<String>()
            val fetchJobs = mutableListOf<Deferred<DlnaDevice?>>()

            for (target in searchTargets) {
                val requestBytes = createSsdpRequest(target)
                val packet = DatagramPacket(requestBytes, requestBytes.size, multicastAddress, 1900)
                repeat(3) {
                    socket.send(packet)
                    delay(300)
                }
            }

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)

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
                                    detailsClient.fetch(usn, st, location)
                                }
                            fetchJobs += job
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // No packet, continue waiting
                }
            }
            socket.close()

            val result =
                fetchJobs.awaitAll()
                    .filterNotNull()
                    .filter { it.deviceType.contains("MediaRenderer") }

            result.forEach {
                Log.d(
                    "SSDP",
                    "⏵ ${it.friendlyName} (${it.modelName}, ${it.deviceType}) @ ${it.location}",
                )
            }
            result
        }

    private fun createSsdpRequest(st: String): ByteArray {
        val request =
            """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 5
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
