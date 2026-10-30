package io.github.scovillo.playondlna.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackPositionInfo
import io.github.scovillo.playondlna.dlna.control.PlaybackSession
import io.github.scovillo.playondlna.dlna.control.TransportState
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

internal fun uiSession(
    state: TransportState,
    knownMedia: Boolean = true,
): PlaybackSession {
    val item = LibraryItem(LibraryMetadata("track", "Track", "Uploader", 60, "720p", false), File("track.mp4"), null, null)
    val device = DlnaDevice("renderer", "MediaRenderer", "http://test/device.xml", "Test renderer", "Test", "Test", "MediaRenderer", "http://test/control", null)
    return PlaybackSession(
        device,
        Playlist("queue", "Session playlist", if (knownMedia) listOf(item) else emptyList()),
        0,
        state,
        PlaybackPositionInfo("http://test/track/video.mp4", 20.0, 1, 60.0),
    )
}

class SessionPlaybackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playsStoppedSession() = verifySessionCommand(TransportState.STOPPED)

    @Test
    fun pausesPlayingSession() = verifySessionCommand(TransportState.PLAYING)

    @Test
    fun resumesPausedSession() = verifySessionCommand(TransportState.PAUSED_PLAYBACK)

    @Test
    fun controlsPlayingRendererWithoutAnAppQueue() = verifySessionCommand(TransportState.PLAYING, knownMedia = false)

    @Test
    fun controlsPausedRendererWithoutAnAppQueue() = verifySessionCommand(TransportState.PAUSED_PLAYBACK, knownMedia = false)

    @Test
    fun navigationButtonsFollowSessionIndex() {
        val session = uiSession(TransportState.PLAYING)
        val queue = Playlist("queue", "Queue", List(3) { session.playlist[0] })
        val current = androidx.compose.runtime.mutableStateOf(session.copy(playlist = queue))
        var nextLabel = ""
        var previousLabel = ""
        val commands = mutableListOf<PlaybackCommand>()
        compose.setContent {
            nextLabel = LocalContext.current.getString(R.string.next)
            previousLabel = LocalContext.current.getString(R.string.previous)
            MaterialTheme {
                DlnaRemoteControl(null, null, current.value, { commands += it }, {})
            }
        }
        compose.onNodeWithContentDescription(previousLabel).assertIsNotEnabled()
        compose.onNodeWithContentDescription(nextLabel).assertIsEnabled().performClick()
        compose.runOnIdle { current.value = current.value.copy(currentIndex = 1) }
        compose.onNodeWithContentDescription(previousLabel).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(nextLabel).assertIsEnabled()
        compose.runOnIdle { current.value = current.value.copy(currentIndex = 2) }
        compose.onNodeWithContentDescription(nextLabel).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(PlaybackCommand.NEXT, PlaybackCommand.PREVIOUS), commands) }
    }

    private fun verifySessionCommand(
        state: TransportState,
        knownMedia: Boolean = true,
    ) {
        val session = uiSession(state, knownMedia)
        val commands = mutableListOf<PlaybackCommand>()
        var playLabel = ""
        compose.setContent {
            playLabel = LocalContext.current.getString(if (state == TransportState.PLAYING) R.string.pause else R.string.play)
            MaterialTheme {
                DlnaRemoteControl(
                    currentThumbnail = null,
                    sponsorBlockSegmentCount = null,
                    session = session,
                    onCommand = { commands += it },
                    onSeek = {},
                )
            }
        }
        compose.onNodeWithText(session.playlist.name).assertExists()
        compose.onNodeWithText(session.device.friendlyName).assertExists()
        compose.onNodeWithContentDescription(playLabel).performClick()
        compose.runOnIdle {
            assertEquals(listOf(if (state == TransportState.PLAYING) PlaybackCommand.PAUSE else PlaybackCommand.PLAY), commands)
        }
    }
}
