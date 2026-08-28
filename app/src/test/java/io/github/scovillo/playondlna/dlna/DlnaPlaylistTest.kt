package io.github.scovillo.playondlna.dlna

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DlnaPlaylistTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun advertisesAudioPlaylistAsAudio() {
        val playlist = DlnaPlaylist(Playlist("id", "Audio", listOf("track")), listOf(audioItem("track", "Track", null)), "http://server")

        assertTrue(playlist.metadataDidlLite().contains("http-get:*:audio/x-mpegurl:*"))
        assertTrue(playlist.metadataDidlLite().contains("http://server/playlists/id/cover.jpg"))
        assertTrue(playlist.url.endsWith("/playlist.m3u"))
    }

    @Test
    fun advertisesMixedPlaylistAsVideo() {
        val playlist =
            DlnaPlaylist(
                Playlist("id", "Mixed", listOf("track", "video")),
                listOf(audioItem("track", "Track", null), videoItem("video", "Video", null)),
                "http://server",
            )

        assertTrue(playlist.metadataDidlLite().contains("http-get:*:application/x-mpegurl:*"))
    }

    @Test
    fun createsAudioPayloadWithCoverMetadata() {
        val playlist = Playlist("list", "Audio", listOf("track"))
        val payload = DlnaPlaylist(playlist, listOf(audioItem("track", "Track", File("cover.jpg"))), "http://server").toPayload()!!

        assertEquals("audio/x-mpegurl", payload.mimeType)
        assertTrue(payload.content.contains("#EXTALBUMARTURL:http://server/playlists/list/cover.jpg"))
        assertTrue(payload.content.contains("/track/audio.mp3"))
        assertTrue(payload.content.contains("tvg-logo=\"http://server/track/cover.jpg\""))
    }

    @Test
    fun usesPlayOnDlnaCoverForAudioEntriesWithoutThumbnail() {
        val playlist = Playlist("list", "Audio", listOf("track"))
        val payload = DlnaPlaylist(playlist, listOf(audioItem("track", "Track", null)), "http://server").toPayload()!!

        assertTrue(payload.content.contains("#EXTALBUMARTURL:http://server/track/cover.jpg"))
        assertTrue(payload.content.contains("tvg-logo=\"http://server/track/cover.jpg\""))
    }

    @Test
    fun createsOrderedPayloadWithAbsoluteVideoUrlsAndSafeTitles() {
        val playlist = Playlist("list", "My list", listOf("two", "missing", "one"))
        val one = videoItem("one", "First\nvideo", thumbnail = null)
        val two = videoItem("two", "Second", thumbnail = null)

        val payload = DlnaPlaylist(playlist, listOf(one, two), "http://192.168.1.2:8080").toPayload()!!

        assertEquals(
            "#EXTM3U\r\n" +
                "#EXTALBUMARTURL:http://192.168.1.2:8080/playlists/list/cover.jpg\r\n" +
                "#PLAYLIST:My list\r\n" +
                "#EXTINF:10 ,Uploader - Second\r\n" +
                "http://192.168.1.2:8080/two/video.mp4\r\n" +
                "#EXTINF:10 ,Uploader - First video\r\n" +
                "http://192.168.1.2:8080/one/video.mp4\r\n",
            payload.content,
        )
    }

    @Test
    fun returnsNullWhenNoPlaylistEntryExists() {
        val playlist = DlnaPlaylist(Playlist("list", "Empty", listOf("gone")), emptyList(), "http://host:1")

        assertNull(playlist.toPayload())
    }

    @Test
    fun createsVideoAndMixedPayloads() {
        val videoPayload =
            DlnaPlaylist(Playlist("video-list", "Video", listOf("video")), listOf(videoItem("video", "Video", null)), "http://server")
                .toPayload()!!
        val mixedPayload =
            DlnaPlaylist(
                Playlist("mixed-list", "Mixed", listOf("track", "video")),
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
    fun includesTrackCoverForVideoPayloads() {
        val payload =
            DlnaPlaylist(
                Playlist("list", "Video", listOf("video")),
                listOf(videoItem("video", "Video", File("cover.jpg"))),
                "http://server",
            ).toPayload()!!

        assertTrue(payload.content.contains("tvg-logo=\"http://server/video/cover.jpg\""))
        assertTrue(payload.content.contains("#EXTALBUMARTURL:http://server/video/cover.jpg\r\n"))
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
