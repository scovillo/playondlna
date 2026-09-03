package io.github.scovillo.playondlna.dlna

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteDevicesTest {
    @Test
    fun discoversPersistedFavoritesWhenTheyBecomeAvailable() =
        runBlocking {
            val location = "http://192.168.1.10:8080/description.xml"
            val requestedLocations = mutableListOf<String>()
            val favorites = flow { emit(listOf(location)) }

            val devices =
                discoverFavoriteDevices(favorites) { requestedLocation ->
                    requestedLocations += requestedLocation
                    deviceAt(requestedLocation)
                }

            assertEquals(listOf(location), requestedLocations)
            assertEquals(listOf(location), devices.map(DlnaDevice::location))
        }

    private fun deviceAt(location: String) =
        DlnaDevice(
            usn = "uuid:renderer",
            st = "manual",
            location = location,
            friendlyName = "Renderer",
            manufacturer = "Example",
            modelName = "Example Renderer",
            deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1",
            avTransportUrl = "http://192.168.1.10:8080/control",
            renderingControlUrl = null,
        )
}
