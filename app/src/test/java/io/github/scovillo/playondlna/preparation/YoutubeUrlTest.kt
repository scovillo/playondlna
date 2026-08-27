package io.github.scovillo.playondlna.preparation

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeUrlTest {
    private val normalizer = YoutubeUrl()

    @Test
    fun extractsUrlFromMarkdownShareText() {
        assertEquals(
            "https://youtube.com/playlist?list=PLbl2XIl2d6-k&si=Q5SKJPwDESJsyD0F",
            normalizer.normalize(
                "[https://youtube.com/playlist?list=PLbl2XIl2d6-k\\&si=Q5SKJPwDESJsyD0F]" +
                    "(https://youtube.com/playlist?list=PLbl2XIl2d6-k\\&si=Q5SKJPwDESJsyD0F)",
            ),
        )
    }

    @Test
    fun translatesYMusicAppWatchUrlToYoutube() {
        assertEquals(
            "https://www.youtube.com/watch?v=UnztTWtCeG4",
            normalizer.normalize("https://ymusicapp.com/watch?v=UnztTWtCeG4"),
        )
    }

    @Test
    fun translatesYMusicAppUrlFromMarkdownShareText() {
        assertEquals(
            "https://www.youtube.com/watch?v=UnztTWtCeG4",
            normalizer.normalize(
                "[https://ymusicapp.com/watch?v=UnztTWtCeG4]" +
                    "(https://ymusicapp.com/watch?v=UnztTWtCeG4)",
            ),
        )
    }
}
