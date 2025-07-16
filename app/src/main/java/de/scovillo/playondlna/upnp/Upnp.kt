package de.scovillo.playondlna.upnp

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.w3c.dom.Element
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

data class DlnaDeviceDescription(
    val usn: String,
    val st: String,
    val location: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val deviceType: String
)

suspend fun discoverDlnaDevices(timeoutMs: Long = 5000): List<DlnaDeviceDescription> =
    coroutineScope {
        val multicastAddress = InetAddress.getByName("239.255.255.250")
        val port = 1900
        val searchTargets =
            listOf("ssdp:all", "upnp:rootdevice", "urn:schemas-upnp-org:device:MediaRenderer:1")

        val socket = MulticastSocket(port).apply {
            reuseAddress = true
            timeToLive = 4
            soTimeout = 1000
        }

        val seenLocations = mutableSetOf<String>()  // Verhindert doppelte Description-Fetches
        val seenUsns = mutableSetOf<String>()       // Optional: Zum Debuggen oder Logging
        val fetchJobs = mutableListOf<Deferred<DlnaDeviceDescription?>>()

        fun createSsdpRequest(st: String): ByteArray {
            val request = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 2
            ST: $st

        """.trimIndent().replace("\n", "\r\n") + "\r\n"
            return request.toByteArray(Charsets.UTF_8)
        }

        for (st in searchTargets) {
            val requestBytes = createSsdpRequest(st)
            val packet = DatagramPacket(requestBytes, requestBytes.size, multicastAddress, port)
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
            } catch (e: SocketTimeoutException) {
                // no packet, waiting
            }
        }

        socket.close()
        return@coroutineScope fetchJobs.awaitAll().filterNotNull()
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

fun fetchDeviceDescription(usn: String, st: String, location: String): DlnaDeviceDescription? {
    return try {
        val stream = URL(location).openStream()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        doc.documentElement.normalize()
        val device = doc.getElementsByTagName("device").item(0) as? Element ?: return null

        DlnaDeviceDescription(
            usn = usn,
            st = st,
            location = location,
            friendlyName = device.getElementsByTagName("friendlyName").item(0)?.textContent
                ?: "unknown",
            manufacturer = device.getElementsByTagName("manufacturer").item(0)?.textContent
                ?: "unknown",
            modelName = device.getElementsByTagName("modelName").item(0)?.textContent ?: "unknown",
            deviceType = device.getElementsByTagName("deviceType").item(0)?.textContent ?: "unknown"
        )
    } catch (e: Exception) {
        println("Error at $location: ${e.message}")
        null
    }
}
