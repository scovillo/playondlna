package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.model.DlnaDevicesListScreenModel.MediaSession
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MediaSessionDisplayTest {
    private val item = LibraryItem(LibraryMetadata("track", "App title", "Uploader", 60, "720p", false), File("track.mp4"), null, null)
    private val session = MediaSession(listOf(item))

    @Test
    fun identifiesAppPlaybackFromRendererUri() {
        assertEquals(item, session.currentItem(status(item.url), 0))
    }

    @Test
    fun externalPlaybackDoesNotReuseAppTitleEvenWithOldPlaylistIndex() {
        assertNull(session.currentItem(status("plugin://plugin.video.jellyfin/external-track"), 0))
    }

    @Test
    fun omittedTrackUriUsesKnownSessionIndex() {
        assertEquals(item, session.currentItem(status(null), 0))
    }

    private fun status(uri: String?) = PlaybackStatus(TransportState.PLAYING, uri, 2.0, 1, 60.0)
}
