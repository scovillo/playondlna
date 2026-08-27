package io.github.scovillo.playondlna.dlna

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.model.escapeXml

data class DlnaPlaylist(
    val playlist: Playlist,
    val libraryItems: List<LibraryItem>,
    val baseUrl: String,
) {
    data class Payload(
        val title: String,
        val content: String,
        val mimeType: String,
    )

    private val entriesById = libraryItems.associateBy { it.metadata.id }
    private val entries = playlist.videoIds.mapNotNull(entriesById::get)
    private val isAudioOnly = entries.isNotEmpty() && entries.all { it.metadata.isAudioOnly }
    private val mimeType = if (isAudioOnly) "audio/x-mpegurl" else "application/x-mpegurl"

    val title: String = playlist.name
    val url: String = "$baseUrl/playlists/${playlist.id}/playlist.m3u"

    fun toPayload(): Payload? {
        if (entries.isEmpty()) return null
        val content =
            buildString {
                appendM3uLine("#EXTM3U")
                appendM3uLine("#EXTALBUMARTURL:$baseUrl/playlists/${playlist.id}/cover.jpg")
                appendM3uLine("#PLAYLIST:${sanitizeM3uText(playlist.name)}")
                entries.forEach { item ->
                    val metadata = item.metadata
                    val title = sanitizeM3uText(metadata.title)
                    val uploader = sanitizeM3uText(metadata.uploader)
                    val displayTitle = if (title.contains(uploader)) title else "$uploader - $title"
                    val coverUrl =
                        if (metadata.isAudioOnly) {
                            "$baseUrl/${metadata.id}/cover.jpg"
                        } else {
                            item.thumbnail?.let { "$baseUrl/${metadata.id}/cover.jpg" }
                        }
                    if (coverUrl != null) appendM3uLine("#EXTALBUMARTURL:$coverUrl")
                    val attributes =
                        buildList {
                            if (coverUrl != null) {
                                add("logo=\"$coverUrl\"")
                                add("tvg-logo=\"$coverUrl\"")
                            }
                            if (metadata.isAudioOnly) add("radio=\"true\"")
                        }.joinToString(" ")
                    appendM3uLine("#EXTINF:${metadata.durationInSeconds} $attributes,$displayTitle")
                    val fileName = if (metadata.isAudioOnly) "audio.${item.mediaFile.extension.lowercase()}" else "video.mp4"
                    appendM3uLine("$baseUrl/${metadata.id}/$fileName")
                }
            }
        return Payload(title, content, mimeType).also { AppLog.d("DlnaPlaylist", it.toString()) }
    }

    fun metadataDidlLite(): String {
        val coverUrl = "$baseUrl/playlists/${playlist.id}/cover.jpg"
        return """
            <DIDL-Lite
                xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">
                <item id="${playlist.id.escapeXml()}" parentID="0" restricted="1">
                    <dc:title>${title.escapeXml()}</dc:title>
                    <upnp:class>object.item.playlistItem</upnp:class>
                    <upnp:albumArtURI dlna:profileID="JPEG_LRG">${coverUrl.escapeXml()}</upnp:albumArtURI>
                    <res protocolInfo="http-get:*:$mimeType:*">${url.escapeXml()}</res>
                </item>
            </DIDL-Lite>
            """.trimIndent().escapeXml()
    }

    private fun StringBuilder.appendM3uLine(value: String) {
        append(value).append("\r\n")
    }

    private fun sanitizeM3uText(value: String): String = value.replace(Regex("[\\r\\n]+"), " ").trim()
}
