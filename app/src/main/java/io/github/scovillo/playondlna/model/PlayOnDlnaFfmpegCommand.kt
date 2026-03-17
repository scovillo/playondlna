package io.github.scovillo.playondlna.model

import io.github.scovillo.playondlna.stream.PlayOnDlnaVideoStream
import java.io.File
import java.util.Locale

class PlayOnDlnaFfmpegCommand(
    private val streamFiles: PlayOnDlnaVideoStream,
    private val audioHasBestCompatibility: Boolean,
    private val output: File,
    private val subtitleLocale: Locale?
) {

    private val hasSubtitle
        get() = streamFiles.subtitleFile != null && subtitleLocale != null

    fun value(): String {
        val ffmpegCmd = mutableListOf(
            "-i", streamFiles.videoFile.absolutePath,
            "-i", streamFiles.audioFile.absolutePath,
        )
        if (hasSubtitle) {
            ffmpegCmd.addAll(
                listOf(
                    "-fix_sub_duration",
                    "-i", streamFiles.subtitleFile!!.absolutePath
                )
            )
        }
        ffmpegCmd.addAll(
            listOf(
                "-map", "0:v:0", "-map", "1:a:0",
            )
        )
        if (hasSubtitle) {
            ffmpegCmd.addAll(
                listOf(
                    "-map", "2:s:0"
                )
            )
        }
        ffmpegCmd.addAll(listOf("-c:v", "copy"))
        if (audioHasBestCompatibility) {
            ffmpegCmd.addAll(listOf("-c:a", "copy"))
        } else {
            ffmpegCmd.addAll(listOf("-c:a", "aac"))
        }
        if (hasSubtitle) {
            ffmpegCmd.addAll(
                listOf(
                    "-c:s",
                    "mov_text",
                    "-metadata:s:s:0", "language=${subtitleLocale!!.isO3Language}",
                    "-disposition:s:0", "default",
                    "-fflags", "+genpts",
                    "-max_interleave_delta", "0",
                )
            )
        }
        ffmpegCmd.addAll(listOf("-movflags", "faststart", "-shortest", "-y", output.absolutePath))
        return ffmpegCmd.joinToString(" ")
    }
}