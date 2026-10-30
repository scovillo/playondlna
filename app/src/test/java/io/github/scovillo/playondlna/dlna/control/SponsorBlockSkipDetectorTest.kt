package io.github.scovillo.playondlna.dlna.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorBlockSkipDetectorTest {
    private val detector = SponsorBlockSkipDetector(listOf(SponsorSegment(10.0, 20.0)))

    @Test
    fun `seeks to the end after entering a sponsor segment`() {
        assertEquals(20.0, detector.nextSeekPosition(10.0))
    }

    @Test
    fun `same detector can be reused by multiple players`() {
        detector.nextSeekPosition(12.0)

        assertEquals(20.0, detector.nextSeekPosition(15.0))
    }

    @Test
    fun `seeks again after playback is rewound before the segment`() {
        detector.nextSeekPosition(12.0)
        detector.nextSeekPosition(5.0)

        assertEquals(20.0, detector.nextSeekPosition(12.0))
    }

    @Test
    fun `does not seek outside the segment or at its exclusive end`() {
        assertNull(detector.nextSeekPosition(9.0))
        assertNull(detector.nextSeekPosition(20.0))
        assertNull(detector.nextSeekPosition(25.0))
    }

    @Test
    fun `same position produces the same result on repeated playback`() {
        repeat(3) {
            assertEquals(20.0, detector.nextSeekPosition(12.0))
        }
    }
}
