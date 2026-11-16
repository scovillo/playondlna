package io.github.scovillo.playondlna.dlna.transport

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.observation.PlaybackEndDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackEndDetectorTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun ignoresStoppedDuringStartupButEndsOnEarlyStopAfterPlayback() {
        val detector = PlaybackEndDetector()
        assertFalse(detector.observe(TransportState.STOPPED, "media", "media", 0.0, 100.0))
        assertFalse(detector.observe(TransportState.PLAYING, "media", "media", 20.0, 100.0))
        assertTrue(detector.observe(TransportState.STOPPED, "media", "media", 0.0, 0.0))
        assertFalse(detector.hasReachedFileEnd())
    }

    @Test
    fun remembersFileEndWhenRendererResetsPositionOnStop() {
        val detector = PlaybackEndDetector()
        assertFalse(detector.observe(TransportState.PLAYING, "media", "media", 99.0, 100.0))
        assertTrue(detector.observe(TransportState.STOPPED, "media", "media", 0.0, 0.0))
        assertTrue(detector.hasReachedFileEnd())
    }

    @Test
    fun unknownDurationDoesNotAuthorizeAdvancingPlaylist() {
        val detector = PlaybackEndDetector()
        assertFalse(detector.observe(TransportState.PLAYING, "media", "media", 20.0, null))
        assertTrue(detector.observe(TransportState.STOPPED, "media", "media"))
        assertFalse(detector.hasReachedFileEnd())
    }
}
