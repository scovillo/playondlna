package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Subtitle
import io.github.scovillo.playondlna.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MediaFileResourceTest {
    private val address = LocalIpAddress()
    private val port = ServerPort()

    @Test
    fun advertisesConsistentRoutesMimeTypesAndProfilesForAllMediaFormats() {
        for ((extension, audioOnly, profile) in listOf(
            Triple("mp4", false, VideoQuality.default.dlnaProfile),
            Triple("mp3", true, "MP3"),
            Triple("m4a", true, "AAC_ISO_320"),
        )) {
            val item = LibraryItem(LibraryMetadata("id", "Title", "Uploader", 60, VideoQuality.default.qualityName, audioOnly), File("media.$extension"), null, null)
            val resource = MediaFileResource(address, port, item)
            val metadata = metadata(resource)
            val res = metadata.getElementsByTagName("res").item(0)
            assertEquals(resource.url, res.textContent)
            assertTrue(resource.url.endsWith("/id/${item.rendererFileName}"))
            assertTrue(res.attributes.getNamedItem("protocolInfo").nodeValue.contains("http-get:*:${item.mimeType}:DLNA.ORG_PN=$profile;"))
            assertEquals(resource.coverUrl, metadata.getElementsByTagName("upnp:albumArtURI").item(0).textContent)
            assertTrue(resource.coverUrl.endsWith("/id/cover.jpg"))
        }
    }

    @Test
    fun escapesMetadataAndIncludesExternalSubtitleRoute() {
        val item =
            LibraryItem(
                LibraryMetadata("a&b", "Title <one> & two", "Uploader & friends", 60, VideoQuality.default.qualityName, false),
                File("video.mp4"),
                File("cover.jpg"),
                Subtitle(File("a&b.fetchSubtitle.de.srt")),
            )
        val resource = MediaFileResource(address, port, item)
        val metadata = metadata(resource)
        assertEquals(item.metadata.title, metadata.getElementsByTagName("dc:title").item(0).textContent)
        assertEquals(item.metadata.uploader, metadata.getElementsByTagName("dc:creator").item(0).textContent)
        val subtitle = metadata.getElementsByTagName("res").item(1)
        assertEquals(resource.subtitleUrl, subtitle.textContent)
        assertTrue(resource.subtitleUrl.endsWith("/a&b/video.de.srt"))
        assertEquals("de", subtitle.attributes.getNamedItem("xml:lang").nodeValue)
    }

    private fun metadata(resource: MediaFileResource): Document {
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val wrapper = "<metadata>${resource.metaDataDidlLite}</metadata>"
        val didl = parser.parse(ByteArrayInputStream(wrapper.toByteArray())).documentElement.textContent
        return parser.parse(ByteArrayInputStream(didl.toByteArray()))
    }
}
