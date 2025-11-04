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

package io.github.scovillo.playondlna.upnpdlna

import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.w3c.dom.Element
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

data class DlnaDevice(
    val usn: String,
    val st: String,
    val location: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val deviceType: String,
    val avTransportUrl: String?,
    val renderingControlUrl: String?
)

suspend fun discoverDlnaDevices(
    wifiManager: WifiManager,
    timeoutMs: Long = 5000
): List<DlnaDevice> =
    coroutineScope {
        val lock = wifiManager.createMulticastLock("PlayOnDlna:ssdp").apply {
            setReferenceCounted(true)
            acquire()
        }
        try {
            val multicastAddress = InetAddress.getByName("239.255.255.250")
            val searchTargets =
                listOf(
                    "ssdp:all",
                    "upnp:rootdevice",
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:device:MediaServer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1"
                )

            val socket = DatagramSocket(0).apply {
                soTimeout = 1000
            }

            val seenLocations = mutableSetOf<String>()
            val seenUsns = mutableSetOf<String>()
            val fetchJobs = mutableListOf<Deferred<DlnaDevice?>>()

            fun createSsdpRequest(st: String): ByteArray {
                val request = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 5
            ST: $st

        """.trimIndent().replace("\n", "\r\n") + "\r\n"
                return request.toByteArray(Charsets.UTF_8)
            }

            for (st in searchTargets) {
                val requestBytes = createSsdpRequest(st)
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
                    val headers = parseSSDPHeaders(response)

                    val usn = headers["USN"] ?: continue
                    val st = headers["ST"] ?: "unknown"
                    val location = headers["LOCATION"] ?: continue

                    synchronized(seenLocations) {
                        if (location !in seenLocations) {
                            seenLocations += location
                            seenUsns += usn
                            val job = async {
                                fetchDeviceDescription(usn, st, location)
                            }
                            fetchJobs += job
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // no packet, waiting
                }
            }
            socket.close()
            val result = fetchJobs.awaitAll().filterNotNull()
            result.forEach {
                Log.d(
                    "UPNP",
                    "⏵ ${it.friendlyName} (${it.modelName}, ${it.deviceType}) @ ${it.location}"
                )
            }
            return@coroutineScope result
        } finally {
            if (lock.isHeld) {
                lock.release()
            }
        }
    }

fun parseSSDPHeaders(response: String): Map<String, String> {
    return response
        .lines()
        .drop(1)
        .mapNotNull {
            val idx = it.indexOf(':')
            if (idx != -1) it.substring(0, idx).trim().uppercase() to it.substring(idx + 1)
                .trim() else null
        }
        .toMap()
}

fun fetchDeviceDescription(usn: String, st: String, location: String): DlnaDevice? {
    return try {
        val stream = URL(location).openStream()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        doc.documentElement.normalize()
        val device = doc.getElementsByTagName("device").item(0) as? Element ?: return null
        val serviceList = device.getElementsByTagName("serviceList").item(0) as? Element

        var avTransportControlUrl: String? = null
        var renderingControlUrl: String? = null

        if (serviceList != null) {
            val services = serviceList.getElementsByTagName("service")
            for (i in 0 until services.length) {
                val service = services.item(i) as? Element ?: continue
                val serviceType = service.getElementsByTagName("serviceType").item(0)?.textContent
                val controlUrl = service.getElementsByTagName("controlURL").item(0)?.textContent

                if (serviceType == "urn:schemas-upnp-org:service:AVTransport:1") {
                    avTransportControlUrl = resolveUrl(location, controlUrl)
                }
                if (serviceType == "urn:schemas-upnp-org:service:RenderingControl:1") {
                    renderingControlUrl = resolveUrl(location, controlUrl)
                }
            }
        }
        DlnaDevice(
            usn = usn,
            st = st,
            location = location,
            friendlyName = device.getElementsByTagName("friendlyName").item(0)?.textContent
                ?: "unknown",
            manufacturer = device.getElementsByTagName("manufacturer").item(0)?.textContent
                ?: "unknown",
            modelName = device.getElementsByTagName("modelName").item(0)?.textContent ?: "unknown",
            deviceType = device.getElementsByTagName("deviceType").item(0)?.textContent
                ?: "unknown",
            avTransportUrl = avTransportControlUrl,
            renderingControlUrl = renderingControlUrl
        )
    } catch (e: Exception) {
        Log.e("UPNP", "Error at $location: ${e.message}")
        null
    }
}

fun resolveUrl(base: String, path: String?): String? {
    if (path == null) return null
    return try {
        val baseUrl = URL(base)
        URL(
            baseUrl.protocol,
            baseUrl.host,
            baseUrl.port.takeIf { it > 0 } ?: baseUrl.defaultPort,
            path).toString()
    } catch (_: Exception) {
        null
    }
}