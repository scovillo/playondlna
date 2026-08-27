package io.github.scovillo.playondlna.model

import io.github.scovillo.playondlna.server.localIpAddress
import io.github.scovillo.playondlna.server.serverPort
import java.io.File
import java.util.Locale

fun String.escapeXml(): String {
    return this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

class Subtitle(val file: File) {
    fun locale(): Locale {
        val regex = Regex("""^.+\.(\w{2})\.srt$""")
        val match = regex.find(file.name)
        return Locale(match?.groupValues?.get(1) ?: "en")
    }
}

class LibraryItem(
    val metadata: LibraryMetadata,
    val mediaFile: File,
    val thumbnail: File?,
    val subtitle: Subtitle?,
) {

    val mimeType: String
        get() =
            when {
                !metadata.isAudioOnly -> "video/mp4"
                mediaFile.extension.equals("mp3", ignoreCase = true) -> "audio/mpeg"
                else -> "audio/mp4"
            }

    val rendererFileName: String
        get() = if (metadata.isAudioOnly) "audio.${mediaFile.extension.lowercase()}" else "video.mp4"

    val duration: String
        get() {
            val hours = metadata.durationInSeconds / 3600
            val minutes = (metadata.durationInSeconds % 3600) / 60
            val secs = metadata.durationInSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        }

    val url: String
        get() = "http://${localIpAddress.value()}:$serverPort/${metadata.id}/$rendererFileName"

    val coverUrl: String
        get() = "http://${localIpAddress.value()}:$serverPort/${metadata.id}/cover.jpg"

    val subtitleUrl: String
        get() {
            return "http://${localIpAddress.value()}:$serverPort/${metadata.id}/video.${subtitle!!.locale().language}.srt"
        }

    val dlnaProfile: String
        get() {
            return if (metadata.isAudioOnly) {
                if (mediaFile.extension.equals("mp3", ignoreCase = true)) "MP3" else "AAC_ISO_320"
            } else {
                VideoQuality.byQualityName(metadata.qualityName).dlnaProfile
            }
        }

    val sizeInBytes: Long
        get() = mediaFile.length()

    val metaDataDidlLite: String
        get() {
            val mediaClass = if (metadata.isAudioOnly) "object.item.audioItem.musicTrack" else "object.item.videoItem.movie"
            val albumArt = "<upnp:albumArtURI dlna:profileID=\"JPEG_LRG\">${coverUrl.escapeXml()}</upnp:albumArtURI>"
            return """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="${metadata.id.escapeXml()}" parentID="0" restricted="1"><dc:title>${metadata.title.escapeXml()}</dc:title><dc:creator>${metadata.uploader.escapeXml()}</dc:creator><upnp:class>$mediaClass</upnp:class>$albumArt<res protocolInfo="http-get:*:$mimeType:*" duration="${duration.escapeXml()}">${url.escapeXml()}</res>${
                if (subtitle != null) "<res protocolInfo=\"http-get:*:text/srt:*\" xml:lang=\"${subtitle.locale().language}\">${subtitleUrl.escapeXml()}</res>" else ""
            }</item></DIDL-Lite>""".escapeXml()
        }
}
