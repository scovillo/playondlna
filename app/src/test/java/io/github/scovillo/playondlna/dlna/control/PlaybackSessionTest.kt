package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionTest {
    private val session = testSession()

    @Test
    fun nextAndPreviousReflectZeroBasedPlaylistBounds() {
        val first = session.copy(currentIndex = 0)
        val middle = session.copy(currentIndex = 1)
        val last = session.copy(currentIndex = 2)
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)
        assertTrue(middle.hasPrevious)
        assertTrue(middle.hasNext)
        assertTrue(last.hasPrevious)
        assertFalse(last.hasNext)
        assertEquals(0, first.predictIndex(PlaybackCommand.PREVIOUS))
        assertEquals(2, last.predictIndex(PlaybackCommand.NEXT))
        assertEquals(0, middle.predictIndex(PlaybackCommand.PREVIOUS))
        assertEquals(2, middle.predictIndex(PlaybackCommand.NEXT))
        assertEquals(middle.currentItem, middle.playlist[1])
    }

    @Test
    fun unknownOrOutOfBoundsIndexDisablesNavigation() {
        for (index in listOf(-1, 3)) {
            val unknown = session.copy(currentIndex = index)
            assertFalse(unknown.hasPrevious)
            assertFalse(unknown.hasNext)
        }
    }

    @Test
    fun observationFindsQueueIndexFromTrackUriAndPreservesPlaylist() {
        val position = PlaybackPositionInfo("http://media/two/video.mp4", 18.0, 1, 60.0)
        val updated = session.copy(state = TransportState.PAUSED_PLAYBACK, positionInfo = position)
        assertEquals(1, updated.currentIndex)
        assertEquals("two", updated.currentItem!!.metadata.id)
        assertEquals(18.0, updated.currentPosition, 0.0)
        assertEquals(TransportState.PAUSED_PLAYBACK, updated.currentState)
        assertSame(session.playlist, updated.playlist)
        assertEquals(0, session.currentIndex)
    }

    @Test
    fun foreignTrackUriHasNoQueueIndex() {
        val updated = session.copy(state = TransportState.PLAYING, positionInfo = PlaybackPositionInfo("plugin://foreign/track"))
        assertEquals(-1, updated.currentIndex)
    }

    @Test
    fun emptySessionCanBeRegisteredBeforePlayback() {
        val empty = PlaybackSession.init(testDevice(), Playlist.empty(), TransportState.UNKNOWN)
        assertNull(empty.currentItem)
        assertFalse(empty.hasNext)
        assertFalse(empty.hasPrevious)
        assertEquals(TransportState.UNKNOWN, empty.currentState)
    }
}
