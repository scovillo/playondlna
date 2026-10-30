package io.github.scovillo.playondlna.dlna

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.VideoQuality
import io.github.scovillo.playondlna.persistence.PlaylistPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DlnaPlaylistPersistenceTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun advertisesAudioPlaylistAsAudio() {
        val playlistPersistence = DlnaPlaylist(PlaylistPersistence("id", "Audio", listOf("track")), listOf(audioItem("track", "Track", null)), "http://server")

        assertTrue(playlistPersistence.metadataDidlLite().contains("http-get:*:audio/x-mpegurl:*"))
        assertTrue(playlistPersistence.metadataDidlLite().contains("http://server/playlists/id/cover.jpg"))
        assertTrue(playlistPersistence.url.endsWith("/playlist.m3u"))
    }

    @Test
    fun advertisesMixedPlaylistAsVideo() {
        val playlistPersistence =
            DlnaPlaylist(
                PlaylistPersistence("id", "Mixed", listOf("track", "video")),
                listOf(audioItem("track", "Track", null), videoItem("video", "Video", null)),
                "http://server",
            )

        assertTrue(playlistPersistence.metadataDidlLite().contains("http-get:*:application/x-mpegurl:*"))
    }

    @Test
    fun createsAudioPayloadWithoutExtraMetadata() {
        val playlistPersistence = PlaylistPersistence("list", "Audio", listOf("track"))
        val payload = DlnaPlaylist(playlistPersistence, listOf(audioItem("track", "Track", File("cover.jpg"))), "http://server").toPayload()!!

        assertEquals("audio/x-mpegurl", payload.mimeType)
        assertFalse(payload.content.contains("#EXTALBUMARTURL"))
        assertTrue(payload.content.contains("/track/audio.mp3"))
        assertFalse(payload.content.contains("tvg-logo"))
    }

    @Test
    fun omitsCoverMetadataForAudioEntriesWithoutThumbnail() {
        val playlistPersistence = PlaylistPersistence("list", "Audio", listOf("track"))
        val payload = DlnaPlaylist(playlistPersistence, listOf(audioItem("track", "Track", null)), "http://server").toPayload()!!

        assertFalse(payload.content.contains("#EXTALBUMARTURL"))
        assertFalse(payload.content.contains("tvg-logo"))
    }

    @Test
    fun createsOrderedPayloadWithAbsoluteVideoUrlsAndSafeTitles() {
        val playlistPersistence = PlaylistPersistence("list", "My list", listOf("two", "missing", "one"))
        val one = videoItem("one", "First\nvideo", thumbnail = null)
        val two = videoItem("two", "Second", thumbnail = null)

        val payload = DlnaPlaylist(playlistPersistence, listOf(one, two), "http://192.168.1.2:8080").toPayload()!!

        assertEquals(
            "#EXTM3U\r\n" +
                "#EXTINF:10,Uploader - Second\r\n" +
                "http://192.168.1.2:8080/two/video.mp4\r\n" +
                "#EXTINF:10,Uploader - First video\r\n" +
                "http://192.168.1.2:8080/one/video.mp4\r\n",
            payload.content,
        )
    }

    @Test
    fun returnsNullWhenNoPlaylistEntryExists() {
        val playlistPersistence = DlnaPlaylist(PlaylistPersistence("list", "Empty", listOf("gone")), emptyList(), "http://host:1")

        assertNull(playlistPersistence.toPayload())
    }

    @Test
    fun createsVideoAndMixedPayloads() {
        val videoPayload =
            DlnaPlaylist(PlaylistPersistence("video-list", "Video", listOf("video")), listOf(videoItem("video", "Video", null)), "http://server")
                .toPayload()!!
        val mixedPayload =
            DlnaPlaylist(
                PlaylistPersistence("mixed-list", "Mixed", listOf("track", "video")),
                listOf(audioItem("track", "Track", null), videoItem("video", "Video", null)),
                "http://server",
            ).toPayload()!!

        assertEquals("application/x-mpegurl", videoPayload.mimeType)
        assertTrue(videoPayload.content.contains("/video/video.mp4"))
        assertEquals("application/x-mpegurl", mixedPayload.mimeType)
        assertTrue(mixedPayload.content.contains("/track/audio.mp3"))
        assertTrue(mixedPayload.content.contains("/video/video.mp4"))
    }

    @Test
    fun omitsTrackCoverForVideoPayloads() {
        val payload =
            DlnaPlaylist(
                PlaylistPersistence("list", "Video", listOf("video")),
                listOf(videoItem("video", "Video", File("cover.jpg"))),
                "http://server",
            ).toPayload()!!

        assertFalse(payload.content.contains("tvg-logo"))
        assertFalse(payload.content.contains("#EXTALBUMARTURL"))
    }

    private fun audioItem(
        id: String,
        title: String,
        thumbnail: File?,
    ): LibraryItem =
        LibraryItem(
            LibraryMetadata(
                id,
                title,
                "Uploader",
                10,
                isAudioOnly = true,
                qualityName = "Audio",
            ),
            File("$id.mp3"),
            thumbnail,
            null,
        )

    private fun videoItem(
        id: String,
        title: String,
        thumbnail: File?,
    ): LibraryItem =
        LibraryItem(
            LibraryMetadata(
                id,
                title,
                "Uploader",
                10,
                isAudioOnly = false,
                qualityName = VideoQuality.default.qualityName,
            ),
            File("$id.mp4"),
            thumbnail,
            null,
        )
}
