package io.github.scovillo.playondlna.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import io.github.scovillo.playondlna.dlna.control.PlaybackStatus
import io.github.scovillo.playondlna.dlna.control.TransportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SeekSliderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun draggingSendsOneSeekOnlyAfterReleaseDespitePlaybackUpdates() {
        val status = mutableStateOf(PlaybackStatus(TransportState.PLAYING, "http://test/video.mp4", 8.0, 1, 193.0))
        val seeks = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme {
                DlnaRemoteControl(
                    currentVideo = null,
                    currentThumbnail = null,
                    playlist = null,
                    sponsorBlockSegmentCount = null,
                    selectedDevice = null,
                    playbackStatus = status.value,
                    playlistIndex = null,
                    playlistSize = 0,
                    onCommand = {},
                    onSeek = { seeks += it },
                )
            }
        }
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertExists()
        slider.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.4f, centerY), 100)
        }
        compose.runOnIdle {
            assertTrue(seeks.isEmpty())
            status.value = status.value.copy(positionSeconds = 9.0)
        }
        slider.performTouchInput {
            moveTo(Offset(width * 0.5f, centerY), 100)
            moveTo(Offset(width * 0.7f, centerY), 100)
        }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        slider.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue(seeks.single() > 100.0)
            status.value = status.value.copy(positionSeconds = seeks.single())
        }
        compose.runOnIdle { assertEquals(1, seeks.size) }
    }
}
