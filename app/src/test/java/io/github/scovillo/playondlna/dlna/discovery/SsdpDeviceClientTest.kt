package io.github.scovillo.playondlna.dlna.discovery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class SsdpDeviceClientTest {
    @Test
    fun parsesAllServicesFromDeviceDescription() {
        val device =
            SsdpDeviceClient().parseDeviceDescription(
                usn = "uuid:renderer",
                st = "urn:schemas-upnp-org:device:MediaRenderer:1",
                location = "http://192.168.1.10:8080/description.xml",
                inputStream = ByteArrayInputStream(DEVICE_DESCRIPTION.toByteArray()),
            )!!

        assertEquals(2, device.services.size)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", device.services[0].serviceType)
        assertEquals("urn:upnp-org:serviceId:AVTransport", device.services[0].serviceId)
        assertEquals("http://192.168.1.10:8080/avtransport/control", device.services[0].controlUrl)
        assertEquals("http://192.168.1.10:8080/avtransport.xml", device.services[0].scpdUrl)
        assertEquals("http://192.168.1.10:8080/avtransport/event", device.services[0].eventSubUrl)
        assertEquals(device.services[0].controlUrl, device.avTransportUrl)
        assertEquals(device.services[1].controlUrl, device.renderingControlUrl)
    }

    private companion object {
        val DEVICE_DESCRIPTION =
            """
            <root>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Renderer</friendlyName>
                <manufacturer>Example</manufacturer>
                <modelName>Example Renderer</modelName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                    <SCPDURL>/avtransport.xml</SCPDURL>
                    <controlURL>/avtransport/control</controlURL>
                    <eventSubURL>/avtransport/event</eventSubURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                    <SCPDURL>/rendering.xml</SCPDURL>
                    <controlURL>/rendering/control</controlURL>
                    <eventSubURL>/rendering/event</eventSubURL>
                  </service>
                </serviceList>
              </device>
            </root>
            """.trimIndent()
    }
}
