package io.github.scovillo.playondlna.preparation

import io.github.scovillo.playondlna.download.PlayOnDlnaVideoInput
import java.io.File

class PlayOnDlnaFfmpegCommand(
    private val streamFiles: PlayOnDlnaVideoInput,
    private val audioHasBestCompatibility: Boolean,
    private val output: File,
    private val isInternalSubtitleEnabled: Boolean,
    private val remoteAudioUrl: String? = null,
) {
    private val isAudioOnly
        get() = streamFiles.videoFile == null

    private val hasSubtitle
        get() = !isAudioOnly && streamFiles.subtitle != null && isInternalSubtitleEnabled

    fun value(): String {
        val ffmpegCmd = mutableListOf<String>()
        if (!isAudioOnly) {
            ffmpegCmd.addAll(listOf("-i", requireNotNull(streamFiles.videoFile).absolutePath))
        }
        if (streamFiles.audioFile != null) {
            ffmpegCmd.addAll(listOf("-i", streamFiles.audioFile.absolutePath))
        } else if (remoteAudioUrl != null) {
            ffmpegCmd.addAll(listOf("-i", remoteAudioUrl))
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
        if (isAudioOnly) {
            ffmpegCmd.add("-vn")
        } else {
            ffmpegCmd.addAll(listOf("-c:v", "copy"))
        }
        if (streamFiles.audioFile != null || remoteAudioUrl != null) {
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
        ffmpegCmd.addAll(listOf("-movflags", "faststart"))
        if (!isAudioOnly) ffmpegCmd.add("-shortest")
        ffmpegCmd.addAll(listOf("-y", output.absolutePath))
        return ffmpegCmd.joinToString(" ")
    }
}
