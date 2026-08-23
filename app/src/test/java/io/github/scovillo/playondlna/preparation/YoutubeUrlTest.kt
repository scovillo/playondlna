package io.github.scovillo.playondlna.preparation

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeUrlTest {
    @Test
    fun extractsUrlFromMarkdownShareText() {
        assertEquals(
            "https://youtube.com/playlist?list=PLbl2XIl2d6-k&si=Q5SKJPwDESJsyD0F",
            youtubeUrlFromSharedText(
                "[https://youtube.com/playlist?list=PLbl2XIl2d6-k\\&si=Q5SKJPwDESJsyD0F]" +
                    "(https://youtube.com/playlist?list=PLbl2XIl2d6-k\\&si=Q5SKJPwDESJsyD0F)",
            ),
        )
    }
}
