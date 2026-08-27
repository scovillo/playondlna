package io.github.scovillo.playondlna.preparation

import io.github.scovillo.playondlna.download.PlayOnDlnaVideoInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayOnDlnaFfmpegCommandTest {
    @Test
    fun `copies an existing video stream`() {
        val command =
            PlayOnDlnaFfmpegCommand(
                PlayOnDlnaVideoInput(File("video.tmp"), File("audio.tmp"), null),
                audioHasBestCompatibility = true,
                output = File("output.mp4"),
                isInternalSubtitleEnabled = false,
            ).value()

        assertTrue(command.contains("-c:v copy"))
        assertFalse(command.contains("-loop 1"))
    }

    @Test
    fun `creates audio-only mp4 without video encoding`() {
        val command =
            PlayOnDlnaFfmpegCommand(
                PlayOnDlnaVideoInput(null, File("audio.tmp"), null),
                audioHasBestCompatibility = false,
                output = File("output.m4a"),
                isInternalSubtitleEnabled = false,
            ).value()

        assertTrue(command.contains("-vn"))
        assertFalse(command.contains("-c:v"))
        assertTrue(command.contains("-c:a aac"))
        assertFalse(command.contains("-shortest"))
    }

    @Test
    fun `passes remote hls audio directly to ffmpeg`() {
        val command =
            PlayOnDlnaFfmpegCommand(
                PlayOnDlnaVideoInput(null, null, null),
                audioHasBestCompatibility = false,
                output = File("output.m4a"),
                isInternalSubtitleEnabled = false,
                remoteAudioUrl = "https://example.com/audio.m3u8",
            ).value()

        assertTrue(command.contains("-i https://example.com/audio.m3u8"))
        assertTrue(command.contains("-c:a aac"))
    }
}
