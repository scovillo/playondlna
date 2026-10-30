package io.github.scovillo.playondlna.model

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


    val dlnaProfile: String
        get() {
            return if (metadata.isAudioOnly) {
                if (mediaFile.extension.equals("mp3", ignoreCase = true)) "MP3" else "AAC_ISO_320"
            } else {
                VideoQuality.byQualityName(metadata.qualityName).dlnaProfile
            }
        }

    val dlnaContentFeatures: String
        get() =
            "DLNA.ORG_PN=$dlnaProfile;DLNA.ORG_OP=01;" +
                "DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    val sizeInBytes: Long
        get() = mediaFile.length()

}
