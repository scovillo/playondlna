package io.github.scovillo.playondlna.dlna.transport

import io.github.scovillo.playondlna.dlna.observation.SponsorBlockSkipDetector
import io.github.scovillo.playondlna.dlna.observation.SponsorSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorBlockSkipDetectorTest {
    private val detector = SponsorBlockSkipDetector(listOf(SponsorSegment(10.0, 20.0)))

    @Test
    fun seeksOnlyInsideTheSegmentWithAnExclusiveEnd() {
        assertNull(detector.nextSeekPosition(9.0))
        assertEquals(20.0, detector.nextSeekPosition(10.0))
        assertEquals(20.0, detector.nextSeekPosition(19.9))
        assertNull(detector.nextSeekPosition(20.0))
        assertNull(detector.nextSeekPosition(25.0))
    }

    @Test
    fun sharedDetectorDependsOnlyOnEachPlayersPositionIncludingReplay() {
        assertEquals(20.0, detector.nextSeekPosition(12.0))
        assertEquals(20.0, detector.nextSeekPosition(15.0))
        assertNull(detector.nextSeekPosition(5.0))
        assertEquals(20.0, detector.nextSeekPosition(12.0))
    }
}
