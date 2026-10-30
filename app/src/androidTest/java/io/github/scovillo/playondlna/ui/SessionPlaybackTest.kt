package io.github.scovillo.playondlna.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackStatus
import io.github.scovillo.playondlna.dlna.control.TransportState
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class SessionPlaybackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playsStoppedPlayerSession() = verifySessionCommand(TransportState.STOPPED)

    @Test
    fun pausesPlayingPlayerSession() = verifySessionCommand(TransportState.PLAYING)

    @Test
    fun resumesPausedPlayerSession() = verifySessionCommand(TransportState.PAUSED_PLAYBACK)

    @Test
    fun externalPlayingShowsPlaybackInProgress() = verifySessionCommand(TransportState.PLAYING, knownMedia = false)

    @Test
    fun externalPausedShowsPausedPlayback() = verifySessionCommand(TransportState.PAUSED_PLAYBACK, knownMedia = false)

    @Test
    fun idlePlayerShowsNoPlayback() = verifySessionCommand(TransportState.STOPPED, knownMedia = false)

    private fun verifySessionCommand(
        oldState: TransportState,
        knownMedia: Boolean = true,
    ) {
        val selected = LibraryItem(LibraryMetadata("new-file", "Selected file", "Uploader", 60, "720p", false), File("new.mp4"), null, null)
        val renderer = DlnaDevice("renderer", "MediaRenderer", "http://test/device.xml", "Test", "Test", "Test", "MediaRenderer", "http://test/avtransport", null)
        val commands = mutableListOf<PlaybackCommand>()
        var playLabel = ""
        var title = ""
        compose.setContent {
            playLabel = LocalContext.current.getString(if (oldState == TransportState.PLAYING) R.string.pause else R.string.play)
            title =
                if (knownMedia) {
                    selected.metadata.title
                } else {
                    LocalContext.current.getString(
                        when (oldState) {
                            TransportState.PLAYING -> R.string.playback_running
                            TransportState.PAUSED_PLAYBACK -> R.string.playback_paused
                            else -> R.string.playback_idle
                        },
                    )
                }
            MaterialTheme {
                DlnaRemoteControl(
                    currentVideo = if (knownMedia) selected else null,
                    currentThumbnail = null,
                    playlist = null,
                    sponsorBlockSegmentCount = null,
                    selectedDevice = renderer,
                    session = PlaybackStatus(oldState, "http://test/old-playlist-track.mp4", 20.0, 3, 60.0),
                    playlistIndex = 2,
                    playlistSize = 0,
                    onCommand = { commands += it },
                    onSeek = {},
                )
            }
        }
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithContentDescription(playLabel).performClick()
        compose.runOnIdle {
            assertEquals(listOf(if (oldState == TransportState.PLAYING) PlaybackCommand.PAUSE else PlaybackCommand.PLAY), commands)
        }
    }
}
