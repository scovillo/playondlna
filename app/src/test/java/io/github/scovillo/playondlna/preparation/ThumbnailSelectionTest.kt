package io.github.scovillo.playondlna.preparation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.Image

class ThumbnailSelectionTest {
    @Test
    fun `selects highest resolution thumbnail`() {
        val thumbnails =
            listOf(
                Image("low.jpg", 100, 100, Image.ResolutionLevel.LOW),
                Image("high-small.jpg", 500, 500, Image.ResolutionLevel.HIGH),
                Image("high-large.jpg", 1000, 1000, Image.ResolutionLevel.HIGH),
                Image("medium.jpg", 800, 800, Image.ResolutionLevel.MEDIUM),
            )

        assertEquals("high-large.jpg", thumbnails.bestThumbnailUrl())
    }
}
