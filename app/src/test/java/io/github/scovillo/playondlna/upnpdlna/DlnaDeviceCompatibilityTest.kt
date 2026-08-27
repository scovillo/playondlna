package io.github.scovillo.playondlna.upnpdlna

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaDeviceCompatibilityTest {
    @Test
    fun identifiesKodiByRendererMetadata() {
        assertTrue(device(modelName = "Kodi").isKodiRenderer())
        assertTrue(device(friendlyName = "Wohnzimmer (Kodi)").isKodiRenderer())
        assertFalse(device(modelName = "Generic MediaRenderer").isKodiRenderer())
    }

    private fun device(
        friendlyName: String = "Renderer",
        manufacturer: String = "Unknown",
        modelName: String = "Unknown",
    ) = DlnaDevice(
        usn = "uuid:test",
        st = "urn:schemas-upnp-org:device:MediaRenderer:1",
        location = "http://192.168.1.2/device.xml",
        friendlyName = friendlyName,
        manufacturer = manufacturer,
        modelName = modelName,
        deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1",
        avTransportUrl = "http://192.168.1.2/AVTransport/control",
        renderingControlUrl = null,
    )
}
