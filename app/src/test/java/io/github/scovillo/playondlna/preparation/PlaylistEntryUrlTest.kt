package io.github.scovillo.playondlna.preparation

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistEntryUrlTest {
    @Test
    fun `removes duplicate slashes from Bandcamp playlist paths`() {
        assertEquals(
            "https://donatell.bandcamp.com/track/panting-hum",
            normalizePlaylistEntryUrl("https://donatell.bandcamp.com//track/panting-hum"),
        )
    }

    @Test
    fun `preserves URL scheme`() {
        assertEquals("https://example.com/path", normalizePlaylistEntryUrl("https://example.com/path"))
    }
}
