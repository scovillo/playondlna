package io.github.scovillo.playondlna.preparation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `keeps simple extractor ids unchanged`() {
        assertEquals("2365710077", safeMediaId("2365710077"))
    }

    @Test
    fun `creates stable filesystem-safe id from Bandcamp URL`() {
        val id = safeMediaId("https://donatell.bandcamp.com/track/water-ca")

        assertEquals(id, safeMediaId("https://donatell.bandcamp.com/track/water-ca"))
        assertTrue(id.matches(Regex("media_[a-f0-9]{32}")))
    }
}
