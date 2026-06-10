package io.github.scovillo.playondlna.preparation

import io.github.scovillo.playondlna.download.PlayOnDlnaVideoInput
import java.io.File

class PlayOnDlnaFfmpegCommand(
    private val streamFiles: PlayOnDlnaVideoInput,
    private val audioHasBestCompatibility: Boolean,
    private val output: File,
    private val isInternalSubtitleEnabled: Boolean,
) {
    private val hasSubtitle
        get() = streamFiles.subtitle != null && isInternalSubtitleEnabled

    fun value(): String {
        val ffmpegCmd =
            mutableListOf(
                "-i",
                streamFiles.videoFile.absolutePath,
            )
        if (streamFiles.audioFile != null) {
            ffmpegCmd.addAll(listOf("-i", streamFiles.audioFile.absolutePath))
        }
        if (hasSubtitle) {
            ffmpegCmd.addAll(
                listOf(
                    "-fix_sub_duration",
                    "-i",
                    streamFiles.subtitle!!.file.absolutePath,
                ),
            )
        }
        ffmpegCmd.addAll(listOf("-c:v", "copy"))
        if (streamFiles.audioFile != null) {
            if (audioHasBestCompatibility) {
                ffmpegCmd.addAll(listOf("-c:a", "copy"))
            } else {
                ffmpegCmd.addAll(listOf("-c:a", "aac"))
            }
        }
        if (hasSubtitle) {
            ffmpegCmd.addAll(
                listOf(
                    "-c:s",
                    "mov_text",
                    "-metadata:s:s:0", "language=${streamFiles.subtitle!!.locale().isO3Language}",
                    "-disposition:s:0", "default",
                    "-fflags", "+genpts",
                    "-max_interleave_delta", "0",
                ),
            )
        }
        ffmpegCmd.addAll(listOf("-movflags", "faststart", "-shortest", "-y", output.absolutePath))
        return ffmpegCmd.joinToString(" ")
    }
}
