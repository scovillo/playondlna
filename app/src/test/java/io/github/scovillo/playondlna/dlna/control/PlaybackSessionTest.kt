package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PlaybackSessionTest {
    private val session =
        PlaybackSession(
            device =
                DlnaDevice(
                    usn = "test",
                    st = "MediaRenderer",
                    location = "http://test/device.xml",
                    friendlyName = "Test",
                    manufacturer = "Test",
                    modelName = "Test",
                    deviceType = "MediaRenderer",
                    avTransportUrl = "http://test/avtransport",
                    renderingControlUrl = null,
                ),
            items = listOf("one", "two", "three").map(::item),
            currentIndex = 1,
        )

    @Test
    fun movesWithinPlaylistBounds() {
        assertEquals(2, session.nextIndex(PlaybackCommand.NEXT))
        assertEquals(0, session.nextIndex(PlaybackCommand.PREVIOUS))
    }

    @Test
    fun staysAtBounds() {
        assertEquals(1, session.copy(currentIndex = 2).nextIndex(PlaybackCommand.NEXT))
        assertEquals(0, session.copy(currentIndex = 0).nextIndex(PlaybackCommand.PREVIOUS))
    }

    private fun item(id: String) =
        LibraryItem(
            metadata = LibraryMetadata(id, id, "Test", 10, "720p", false),
            mediaFile = File("$id.mp4"),
            thumbnail = null,
            subtitle = null,
        )
}
