package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.VideoQuality
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoFileCoverMetadataTest {
    @Test
    fun includesAlbumArtForVideo() {
        val media =
            LibraryItem(
                LibraryMetadata(
                    id = "id",
                    title = "title",
                    uploader = "uploader",
                    durationInSeconds = 10,
                    isAudioOnly = false,
                    qualityName = VideoQuality.default.qualityName,
                ),
                mediaFile = File("video.mp4"),
                thumbnail = File("cover.jpg"),
                subtitle = null,
            )

        assertTrue(media.metaDataDidlLite.contains("upnp:albumArtURI"))
        assertTrue(media.metaDataDidlLite.contains("/id/cover.jpg"))
    }

    @Test
    fun includesPlayOnDlnaCoverForAudioWithoutThumbnail() {
        val media =
            LibraryItem(
                LibraryMetadata(
                    id = "id",
                    title = "title",
                    uploader = "uploader",
                    durationInSeconds = 10,
                    isAudioOnly = true,
                    qualityName = "Audio",
                ),
                mediaFile = File("audio.mp3"),
                thumbnail = null,
                subtitle = null,
            )
        assertTrue(media.metaDataDidlLite.contains("upnp:albumArtURI"))
        assertTrue(media.metaDataDidlLite.contains("/id/cover.jpg"))
    }
}
