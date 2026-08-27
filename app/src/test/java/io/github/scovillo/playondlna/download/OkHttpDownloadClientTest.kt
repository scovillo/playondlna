package io.github.scovillo.playondlna.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpDownloadClientTest {
    @Test
    fun `allows slow extractor responses`() {
        val client = createExtractorHttpClient()

        assertEquals(
            TimeUnit.SECONDS.toMillis(30),
            client.readTimeoutMillis.toLong(),
        )
    }

    @Test
    fun `recognizes media ccc conference details`() {
        assertEquals(
            true,
            "https://api.media.ccc.de/public/conferences/jh26".isMediaCccConferenceUrl(),
        )
        assertEquals(
            false,
            "https://api.media.ccc.de/public/events/jh26berlin-sifa".isMediaCccConferenceUrl(),
        )
    }
}
