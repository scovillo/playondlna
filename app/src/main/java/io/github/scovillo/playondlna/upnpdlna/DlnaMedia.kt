package io.github.scovillo.playondlna.upnpdlna

import io.github.scovillo.playondlna.server.escapeXml

data class DlnaMedia(
    val url: String,
    val metaData: String,
    val title: String? = null,
)

fun playlistMedia(
    id: String,
    title: String,
    baseUrl: String,
    isAudioOnlyPlaylist: Boolean = false,
): DlnaMedia {
    val url = "$baseUrl/playlists/$id/playlist.m3u"
    val playlistMimeType = if (isAudioOnlyPlaylist) "video/x-mpegurl" else "audio/mpegurl"
    val metadata =
        (
            """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">""" +
                """<item id="${id.escapeXml()}" parentID="0" restricted="1"><dc:title>${title.escapeXml()}</dc:title>""" +
                """<upnp:class xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">object.container.playlistContainer</upnp:class>""" +
                """<res protocolInfo="http-get:*:$playlistMimeType:*">${url.escapeXml()}</res></item></DIDL-Lite>"""
        ).escapeXml()
    return DlnaMedia(url, metadata, title)
}

fun DlnaMedia.startingAt(index: Int): DlnaMedia {
    require(index >= 0)
    val indexedUrl = "$url?startIndex=$index"
    return DlnaMedia(indexedUrl, metaData.replace(url, indexedUrl))
}
