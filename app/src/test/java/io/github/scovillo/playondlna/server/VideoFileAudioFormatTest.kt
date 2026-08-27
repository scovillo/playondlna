package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VideoFileAudioFormatTest {
    @Test
    fun `advertises native mp3 consistently`() {
        val media = audioFile(File("track.mp3"), "MP3")

        assertEquals("audio/mpeg", media.mimeType)
        assertEquals("audio.mp3", media.rendererFileName)
    }

    @Test
    fun `continues to advertise m4a`() {
        val media = audioFile(File("track.m4a"), "AAC_ISO_320")

        assertEquals("audio/mp4", media.mimeType)
        assertEquals("audio.m4a", media.rendererFileName)
    }

    private fun audioFile(
        file: File,
        profile: String,
    ) = VideoFile("id", "title", "uploader", 10, file, VideoQuality.P1080, null, profile, isAudioOnly = true)
}
