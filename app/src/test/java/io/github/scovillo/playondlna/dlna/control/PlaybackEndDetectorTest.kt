package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.TransportState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackEndDetectorTest {
    @Before
    fun setUp() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun ignoresPreviousTrackStateWhileNextTrackIsStarting() {
        val previousTrack = "http://phone/first/video.mp4"
        val expectedTrack = "http://phone/second/video.mp4"
        val detector = PlaybackEndDetector()

        assertFalse(detector.observe(TransportState.PLAYING, previousTrack, expectedTrack))
        assertFalse(detector.hasObservedPlayback())

        assertFalse(detector.observe(TransportState.STOPPED, previousTrack, expectedTrack))
        assertFalse(detector.hasObservedPlayback())

        assertFalse(detector.observe(TransportState.PLAYING, expectedTrack, expectedTrack))
        assertTrue(detector.hasObservedPlayback())
    }

    @Test
    fun acceptsRendererTrackUriWithRewrittenAuthorityAndQuery() {
        val expectedTrack = "http://phone:8080/video-id/video.mp4"
        val rendererTrack = "http://192.168.1.2/video-id/video.mp4?dlna=1"
        val detector = PlaybackEndDetector()

        assertFalse(detector.observe(TransportState.PLAYING, rendererTrack, expectedTrack))
        assertTrue(detector.observe(TransportState.STOPPED, rendererTrack, expectedTrack))
    }

    @Test
    fun fallsBackAfterStablePlaybackWhenRendererDoesNotReportTrackUri() {
        val expectedTrack = "http://phone/video.mp4"
        val detector = PlaybackEndDetector()

        repeat(3) {
            assertFalse(detector.observe(TransportState.PLAYING, null, expectedTrack))
        }

        assertTrue(detector.observe(TransportState.STOPPED, null, expectedTrack))
    }
}
