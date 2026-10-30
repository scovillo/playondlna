package io.github.scovillo.playondlna.dlna.soap

import org.junit.Assert.assertEquals
import org.junit.Test

class SoapResponseExtractorTest {
    private val extractor = SoapResponseExtractor()

    @Test
    fun parsesNamespacedTrackNumber() {
        assertEquals(2, extractor.parseCurrentTrackNumber("<u:Track>2</u:Track>"))
    }

    @Test
    fun ignoresMissingOrInvalidTrackNumber() {
        assertEquals(0, extractor.parseCurrentTrackNumber("<Track>0</Track>"))
        assertEquals(0, extractor.parseCurrentTrackNumber("<Track>NOT_IMPLEMENTED</Track>"))
        assertEquals(0, extractor.parseCurrentTrackNumber("<RelTime>00:00:12</RelTime>"))
    }
}
