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

import android.util.Log
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.DlnaService
import java.net.URL

/**
 * Fetches and parses UPnP device descriptions from device description URLs.
 */
class SsdpDeviceClient {
    /**
     * Fetch device description XML from location URL and parse it.
     *
     * @param usn Unique Service Name from SSDP discovery
     * @param st Search Target from SSDP discovery
     * @param location Device description URL
     * @return Parsed device information or null if parsing fails
     */
    fun fetch(
        usn: String,
        st: String,
        location: String,
    ): DlnaDevice? {
        return try {
            val stream = URL(location).openStream()
            parseDeviceDescription(usn, st, location, stream)
        } catch (e: Exception) {
            Log.e("DeviceFetcher", "Error at $location: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse a device description XML stream.
     *
     * @return Parsed [DlnaDevice] with all service URLs and metadata
     */
    fun parseDeviceDescription(
        usn: String,
        st: String,
        location: String,
        inputStream: java.io.InputStream,
    ): DlnaDevice? {
        return try {
            inputStream.use { stream ->
                val doc =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(stream)
                doc.documentElement.normalize()

                val device = doc.getElementsByTagName("device").item(0) as? org.w3c.dom.Element ?: return null
                val deviceUsn =
                    device.getElementsByTagName("UDN").item(0)?.textContent
                        ?.takeIf { it.isNotBlank() }
                        ?: usn

                val serviceList =
                    device.getElementsByTagName("serviceList").item(0) as? org.w3c.dom.Element

                val services = parseServices(serviceList, location)
                val avTransportControlUrl =
                    services.firstOrNull { it.serviceType == "urn:schemas-upnp-org:service:AVTransport:1" }?.controlUrl
                val renderingControlUrl =
                    services.firstOrNull { it.serviceType == "urn:schemas-upnp-org:service:RenderingControl:1" }?.controlUrl

                DlnaDevice(
                    usn = deviceUsn,
                    st = st,
                    location = location,
                    friendlyName =
                        device.getElementsByTagName("friendlyName").item(0)?.textContent
                            ?: "unknown",
                    manufacturer =
                        device.getElementsByTagName("manufacturer").item(0)?.textContent
                            ?: "unknown",
                    modelName =
                        device.getElementsByTagName("modelName").item(0)?.textContent
                            ?: "unknown",
                    deviceType =
                        device.getElementsByTagName("deviceType").item(0)?.textContent
                            ?: "unknown",
                    avTransportUrl = avTransportControlUrl,
                    renderingControlUrl = renderingControlUrl,
                    services = services,
                )
            }
        } catch (e: Exception) {
            Log.e("DeviceParser", "Parse error: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parseServices(
        serviceList: org.w3c.dom.Element?,
        location: String,
    ): List<DlnaService> {
        if (serviceList == null) return emptyList()

        val services = serviceList.getElementsByTagName("service")
        return buildList {
            for (i in 0 until services.length) {
                val service = services.item(i) as? org.w3c.dom.Element ?: continue
                val serviceType = service.childTextContent("serviceType") ?: continue
                add(
                    DlnaService(
                        serviceType = serviceType,
                        serviceId = service.childTextContent("serviceId"),
                        scpdUrl = resolveUrl(location, service.childTextContent("SCPDURL")),
                        controlUrl = resolveUrl(location, service.childTextContent("controlURL")),
                        eventSubUrl = resolveUrl(location, service.childTextContent("eventSubURL")),
                    ),
                )
            }
        }
    }

    private fun org.w3c.dom.Element.childTextContent(name: String): String? = getElementsByTagName(name).item(0)?.textContent

    private fun resolveUrl(
        base: String,
        path: String?,
    ): String? {
        if (path == null) return null
        return try {
            val baseUrl = URL(base)
            URL(
                baseUrl.protocol,
                baseUrl.host,
                baseUrl.port.takeIf { it > 0 } ?: baseUrl.defaultPort,
                path,
            ).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
