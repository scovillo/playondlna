package io.github.scovillo.playondlna.download

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

class StreamDownloadUserAgentTest {
    @Test
    fun `video and audio use their own client identity`() {
        assertEquals(
            YoutubeParsingHelper.getVisionOsUserAgent(null),
            StreamDownloadUserAgent("https://rr5.googlevideo.com/videoplayback?c=VISIONOS").value(),
        )
        assertEquals(
            YoutubeParsingHelper.getAndroidUserAgent(null),
            StreamDownloadUserAgent("https://rr5.googlevideo.com/videoplayback?c=ANDROID").value(),
        )
        assertEquals(
            YoutubeParsingHelper.getIosUserAgent(null),
            StreamDownloadUserAgent("https://rr5.googlevideo.com/videoplayback?c=IOS").value(),
        )
    }

    @Test
    fun `missing or unknown clients use Android identity`() {
        listOf(
            "https://rr5.googlevideo.com/videoplayback?c=UNKNOWN",
            "https://rr5.googlevideo.com/videoplayback",
        ).forEach { assertEquals(YoutubeParsingHelper.getAndroidUserAgent(null), StreamDownloadUserAgent(it).value()) }
    }

    @Test
    fun `client query selects identity independently of host`() {
        listOf("https://example.com/media?c=VISIONOS", "https://googlevideo.com.example.com/media?c=VISIONOS").forEach {
            assertEquals(YoutubeParsingHelper.getVisionOsUserAgent(null), StreamDownloadUserAgent(it).value())
        }
    }
}
