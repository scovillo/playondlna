package io.github.scovillo.playondlna.model

import io.github.scovillo.playondlna.upnpdlna.TransportCommand
import io.github.scovillo.playondlna.upnpdlna.playlistIndexForCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistNavigationTest {
    @Test
    fun movesWithinPlaylistBounds() {
        assertEquals(2, playlistIndexForCommand(1, 3, TransportCommand.NEXT))
        assertEquals(0, playlistIndexForCommand(1, 3, TransportCommand.PREVIOUS))
    }

    @Test
    fun doesNotMoveBeyondPlaylistBounds() {
        assertNull(playlistIndexForCommand(3, 3, TransportCommand.NEXT))
        assertNull(playlistIndexForCommand(0, 3, TransportCommand.PREVIOUS))
    }
}
