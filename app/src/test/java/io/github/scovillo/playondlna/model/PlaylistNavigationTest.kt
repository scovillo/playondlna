package io.github.scovillo.playondlna.model

import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.isMixedPlaylist
import io.github.scovillo.playondlna.dlna.control.playlistIndexForCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistNavigationTest {
    @Test
    fun movesWithinPlaylistBounds() {
        assertEquals(2, playlistIndexForCommand(1, 3, PlaybackCommand.NEXT))
        assertEquals(0, playlistIndexForCommand(1, 3, PlaybackCommand.PREVIOUS))
    }

    @Test
    fun doesNotMoveBeyondPlaylistBounds() {
        assertNull(playlistIndexForCommand(3, 3, PlaybackCommand.NEXT))
        assertNull(playlistIndexForCommand(0, 3, PlaybackCommand.PREVIOUS))
    }

    @Test
    fun detectsMixedAudioAndVideoPlaylist() {
        assertTrue(isMixedPlaylist(listOf(media("audio", true), media("video", false))))
        assertFalse(isMixedPlaylist(listOf(media("one", true), media("two", true))))
        assertFalse(isMixedPlaylist(listOf(media("one", false), media("two", false))))
    }

    private fun media(
        id: String,
        isAudioOnly: Boolean,
    ) = LibraryItem(
        LibraryMetadata(
            id = id,
            title = id,
            uploader = "uploader",
            durationInSeconds = 10,
            isAudioOnly = isAudioOnly,
            qualityName = if (isAudioOnly) "Audio" else "Video"
        ),
        mediaFile = File(if (isAudioOnly) "$id.mp3" else "$id.mp4"),
        thumbnail = null,
        subtitle = null,
    )
}
