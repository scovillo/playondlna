package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.persistence.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistM3uTest {
    @Test
    fun createsOrderedM3uWithAbsoluteVideoUrlsAndSafeTitles() {
        val playlist = Playlist("list", "My list", listOf("two", "missing", "one"))
        val m3u = createPlaylistM3u(playlist, listOf(item("one", "First\nvideo"), item("two", "Second")), "http://192.168.1.2:8080")!!

        assertTrue(m3u.content.startsWith("#EXTM3U\n"))
        assertEquals(
            "#EXTM3U\n#EXTINF:-1,Second\nhttp://192.168.1.2:8080/two/video.mp4\n#EXTINF:-1,First video\nhttp://192.168.1.2:8080/one/video.mp4\n",
            m3u.content,
        )
    }

    @Test
    fun returnsNullWhenNoPlaylistVideoExists() {
        assertNull(createPlaylistM3u(Playlist("list", "Empty", listOf("gone")), emptyList(), "http://host:1"))
    }

    @Test
    fun startsAtRequestedEntry() {
        val playlist = Playlist("list", "My list", listOf("one", "two"))
        val m3u = createPlaylistM3u(playlist, listOf(item("one", "First"), item("two", "Second")), "http://server", 1)!!

        assertFalse(m3u.content.contains("/one/video.mp4"))
        assertTrue(m3u.content.contains("/two/video.mp4"))
    }

    @Test
    fun usesAudioUrlForAudioOnlyEntries() {
        val playlist = Playlist("list", "Audio", listOf("track"))
        val metadata = LibraryMetadata("track", "Track", "Uploader", 10, isAudioOnly = true)
        val item = LibraryItem(metadata, File("track.m4a"), null, 1)

        val m3u = createPlaylistM3u(playlist, listOf(item), "http://server")!!

        assertTrue(m3u.content.contains("/track/audio.m4a"))
    }

    @Test
    fun includesLibraryThumbnailAsPlaylistLogo() {
        val playlist = Playlist("list", "Audio", listOf("track"))
        val metadata = LibraryMetadata("track", "Track", "Uploader", 10, isAudioOnly = true)
        val item = LibraryItem(metadata, File("track.m4a"), File("track.thumb.jpg"), 1)

        val m3u = createPlaylistM3u(playlist, listOf(item), "http://server")!!

        assertTrue(m3u.content.contains("tvg-logo=\"http://server/track/cover.jpg\""))
    }

    private fun item(
        id: String,
        title: String,
    ) = LibraryItem(LibraryMetadata(id, title, "Uploader", 10), File("$id.mp4"), null, 1)
}
