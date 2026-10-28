package io.github.scovillo.playondlna.server

import fi.iki.elonen.NanoHTTPD
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class MediaSeekHeadersTest {
    @Test
    fun `advertises byte seeking consistently for full and partial responses`() {
        for ((extension, audioOnly, profile) in listOf(
            Triple("mp4", false, VideoQuality.default.dlnaProfile),
            Triple("mp3", true, "MP3"),
            Triple("m4a", true, "AAC_ISO_320"),
        )) {
            val item =
                LibraryItem(
                    LibraryMetadata("id", "title", "uploader", 1200, VideoQuality.default.qualityName, audioOnly),
                    File("media.$extension"),
                    null,
                    null,
                )
            val expected =
                "DLNA.ORG_PN=$profile;DLNA.ORG_OP=01;" +
                    "DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            assertTrue(item.metaDataDidlLite.contains("http-get:*:${item.mimeType}:$expected"))
            for (status in listOf(NanoHTTPD.Response.Status.OK, NanoHTTPD.Response.Status.PARTIAL_CONTENT)) {
                val response = NanoHTTPD.newFixedLengthResponse(status, item.mimeType, ByteArrayInputStream(ByteArray(10)), 10)
                response.use {
                    if (status == NanoHTTPD.Response.Status.PARTIAL_CONTENT) {
                        it.addHeader("Content-Range", "bytes 10-19/100")
                    }
                    it.addMediaHeaders(item)
                    assertEquals(status, it.status)
                    assertEquals(item.mimeType, it.mimeType)
                    assertEquals(expected, it.getHeader("contentFeatures.dlna.org"))
                    assertEquals("Streaming", it.getHeader("transferMode.dlna.org"))
                    assertEquals("bytes", it.getHeader("Accept-Ranges"))
                    if (status == NanoHTTPD.Response.Status.PARTIAL_CONTENT) {
                        assertEquals("bytes 10-19/100", it.getHeader("Content-Range"))
                    }
                }
            }
        }
    }
}
