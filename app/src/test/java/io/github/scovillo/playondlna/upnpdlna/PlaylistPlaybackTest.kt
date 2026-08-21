package io.github.scovillo.playondlna.upnpdlna

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPlaybackTest {
    @Test
    fun usesM3uUrlAndPlaylistProtocolInfoInSetUriCommand() {
        val media = playlistMedia("id", "List", "http://192.168.1.2:8080")
        val payload = setAvTransportUriPayload(media)

        assertTrue(payload.contains("<CurrentURI>http://192.168.1.2:8080/playlists/id/playlist.m3u</CurrentURI>"))
        assertTrue(payload.contains("audio/mpegurl"))
    }
}
