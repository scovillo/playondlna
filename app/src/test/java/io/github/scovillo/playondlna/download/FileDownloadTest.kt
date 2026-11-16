package io.github.scovillo.playondlna.download

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class FileDownloadTest {
    private fun client(
        code: Int = 206,
        headers: Map<String, String> = emptyMap(),
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder =
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("Test response")
                        .body("x".toResponseBody())
                headers.forEach { (name, value) -> builder.header(name, value) }
                builder.build()
            }.build()

    private fun download(client: OkHttpClient): PlayOnDlnaFileDownload = PlayOnDlnaFileDownload("test", ".tmp", URL, "test", ChunkCalculation(2, 4), client, java.io.File("."))

    @Test
    fun `probe uses complete range length`() {
        assertEquals(12345L, download(client(headers = mapOf("Content-Range" to "bytes 0-0/12345"))).probeDownloadLength())
    }

    @Test
    fun `partial response without total must not use probe body length`() {
        assertEquals(-1L, download(client()).probeDownloadLength())
    }

    @Test
    fun `probe exposes HTTP rejection`() {
        try {
            download(client(code = 403)).probeDownloadLength()
            throw AssertionError("Expected HTTP rejection")
        } catch (error: HttpStatusException) {
            assertEquals(403, error.statusCode)
        }
    }

    @Test
    fun `unknown size downloads full response without range`() =
        runBlocking {
            val directory = Files.createTempDirectory("download-test").toFile()
            var requests = 0
            val client =
                OkHttpClient.Builder().addInterceptor { chain ->
                    requests++
                    if (requests == 2) assertNull(chain.request().header("Range"))
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (requests == 1) 206 else 200)
                        .message("OK")
                        .body("complete media".toResponseBody())
                        .build()
                }.build()
            try {
                val download = PlayOnDlnaFileDownload("test", ".tmp", URL, "test", ChunkCalculation(2, 4), client, directory)
                val result = download.start { }
                assertEquals("complete media", result.readText())
                assertEquals(result.length(), download.totalSize)
                assertEquals(2, requests)
            } finally {
                directory.deleteRecursively()
            }
            assertFalse(directory.exists())
        }

    companion object {
        private const val URL = "https://example.com/media"
    }
}
