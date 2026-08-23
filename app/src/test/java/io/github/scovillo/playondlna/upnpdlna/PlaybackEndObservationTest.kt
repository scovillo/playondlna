package io.github.scovillo.playondlna.upnpdlna

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEndObservationTest {
    @Test
    fun ignoresPreviousTrackStateWhileNextTrackIsStarting() {
        val previousTrack = "http://phone/first/video.mp4"
        val expectedTrack = "http://phone/second/video.mp4"
        var observation = PlaybackEndObservation()

        observation = observation.observe(TransportState.PLAYING, previousTrack, expectedTrack).observation
        val previousTrackStopped = observation.observe(TransportState.STOPPED, previousTrack, expectedTrack)

        assertFalse(previousTrackStopped.playbackEnded)
        assertFalse(previousTrackStopped.observation.playbackObserved)

        observation = previousTrackStopped.observation.observe(TransportState.PLAYING, expectedTrack, expectedTrack).observation
        val transitionStopped = observation.observe(TransportState.STOPPED, expectedTrack, expectedTrack)

        assertFalse(transitionStopped.playbackEnded)

        observation = transitionStopped.observation.observe(TransportState.PLAYING, expectedTrack, expectedTrack).observation
        val expectedTrackStopped = observation.observe(TransportState.STOPPED, expectedTrack, expectedTrack)

        assertTrue(expectedTrackStopped.playbackEnded)
    }

    @Test
    fun acceptsRendererTrackUriWithRewrittenAuthorityAndQuery() {
        val expectedTrack = "http://phone:8080/video-id/video.mp4"
        val rendererTrack = "http://192.168.1.2/video-id/video.mp4?dlna=1"
        var observation = PlaybackEndObservation()

        observation = observation.observe(TransportState.PLAYING, rendererTrack, expectedTrack).observation
        observation = observation.observe(TransportState.PLAYING, rendererTrack, expectedTrack).observation
        val stopped = observation.observe(TransportState.STOPPED, rendererTrack, expectedTrack)

        assertTrue(stopped.playbackEnded)
    }

    @Test
    fun fallsBackAfterStablePlaybackWhenRendererDoesNotReportTrackUri() {
        val expectedTrack = "http://phone/video.mp4"
        var observation = PlaybackEndObservation()

        repeat(3) {
            observation = observation.observe(TransportState.PLAYING, null, expectedTrack).observation
        }
        val stopped = observation.observe(TransportState.STOPPED, null, expectedTrack)

        assertTrue(stopped.playbackEnded)
    }
}
