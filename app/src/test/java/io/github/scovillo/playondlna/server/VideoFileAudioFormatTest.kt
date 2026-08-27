package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VideoFileAudioFormatTest {
    @Test
    fun `advertises native mp3 consistently`() {
        val media = audioFile(File("track.mp3"))

        assertEquals("audio/mpeg", media.mimeType)
        assertEquals("audio.mp3", media.rendererFileName)
    }

    @Test
    fun `continues to advertise m4a`() {
        val media = audioFile(File("track.m4a"))

        assertEquals("audio/mp4", media.mimeType)
        assertEquals("audio.m4a", media.rendererFileName)
    }

    private fun audioFile(
        file: File,
    ) = LibraryItem(
        LibraryMetadata(
            id = "id",
            title = "title",
            uploader = "uploader",
            durationInSeconds = 10,
            isAudioOnly = true,
            qualityName = "Audio"
        ),
        file,
        null,
        null,
    )
}
