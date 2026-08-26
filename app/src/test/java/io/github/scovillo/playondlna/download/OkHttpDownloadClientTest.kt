package io.github.scovillo.playondlna.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpDownloadClientTest {
    @Test
    fun `allows slow extractor responses`() {
        val client = createExtractorHttpClient()

        assertEquals(
            TimeUnit.SECONDS.toMillis(120),
            client.readTimeoutMillis.toLong(),
        )
    }

    @Test
    fun `recognizes media ccc conference details`() {
        assertEquals(
            true,
            isMediaCccConferenceUrl("https://api.media.ccc.de/public/conferences/jh26"),
        )
        assertEquals(
            false,
            isMediaCccConferenceUrl("https://api.media.ccc.de/public/events/jh26berlin-sifa"),
        )
    }
}
