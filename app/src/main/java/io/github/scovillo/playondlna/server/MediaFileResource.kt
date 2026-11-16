package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.escapeXml

class MediaFileResource(private val localIpAddress: LocalIpAddress, private val serverPort: ServerPort, private val item: LibraryItem) {
    val url: String
        get() = "http://${localIpAddress.value()}:${serverPort.value()}/${item.metadata.id}/${item.rendererFileName}"

    val coverUrl: String
        get() = "http://${localIpAddress.value()}:${serverPort.value()}/${item.metadata.id}/cover.jpg"

    val subtitleUrl: String
        get() {
            return "http://${localIpAddress.value()}:${serverPort.value()}/${item.metadata.id}/video.${item.subtitle!!.locale().language}.srt"
        }

    val metaDataDidlLite: String
        get() {
            val mediaClass = if (item.metadata.isAudioOnly) "object.item.audioItem.musicTrack" else "object.item.videoItem.movie"
            val albumArt = "<upnp:albumArtURI dlna:profileID=\"JPEG_LRG\">${coverUrl.escapeXml()}</upnp:albumArtURI>"
            return """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="${item.metadata.id.escapeXml()}" parentID="0" restricted="1"><dc:title>${item.metadata.title.escapeXml()}</dc:title><dc:creator>${item.metadata.uploader.escapeXml()}</dc:creator><upnp:class>$mediaClass</upnp:class>$albumArt<res protocolInfo="http-get:*:${item.mimeType}:${item.dlnaContentFeatures}" duration="${item.duration.escapeXml()}">${url.escapeXml()}</res>${
                if (item.subtitle != null) "<res protocolInfo=\"http-get:*:text/srt:*\" xml:lang=\"${item.subtitle.locale().language}\">${subtitleUrl.escapeXml()}</res>" else ""
            }</item></DIDL-Lite>""".escapeXml()
        }
}
