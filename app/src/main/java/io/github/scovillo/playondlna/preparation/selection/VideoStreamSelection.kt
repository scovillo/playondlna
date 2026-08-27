package io.github.scovillo.playondlna.preparation.selection

import android.util.Log
import io.github.scovillo.playondlna.model.VideoQuality
import io.github.scovillo.playondlna.preparation.hasBestCompatibility
import org.schabi.newpipe.extractor.stream.VideoStream

class VideoStreamSelection(
    private val videoStreams: List<VideoStream>,
    private val quality: VideoQuality,
) {
    fun best(): VideoStream? {
        Log.d(
            "VideoStreams",
            videoStreams.joinToString(System.lineSeparator()) {
                "${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}"
            },
        )
        val compatibleVideoStreams = videoStreams.filter { it.hasBestCompatibility }
        Log.d(
            "compatibleVideoStreams",
            compatibleVideoStreams.joinToString(System.lineSeparator()) {
                "${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}"
            },
        )
        val compatibleVideoStreamsWithPreferredQuality =
            compatibleVideoStreams.sortedByDescending { it.height }
                .filter { it.height <= quality.height }
        Log.d(
            "compatibleVideoStreamsWithPreferredQuality",
            compatibleVideoStreamsWithPreferredQuality.joinToString(System.lineSeparator()) {
                "${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}"
            },
        )
        if (compatibleVideoStreamsWithPreferredQuality.isNotEmpty()) {
            val chosen = compatibleVideoStreamsWithPreferredQuality.maxBy { it.height }
            Log.d(
                "VideoStream",
                "Chosen: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.width}x${chosen.height}, " +
                    "${chosen.quality}, ${chosen.bitrate}, ${chosen.fps}fps",
            )
            return chosen
        }
        if (compatibleVideoStreams.isNotEmpty()) {
            val chosen = compatibleVideoStreams.maxBy { it.height }
            Log.d(
                "VideoStream",
                "Chosen without quality setting: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.width}x${chosen.height}, " +
                    "${chosen.quality}, ${chosen.bitrate}, ${chosen.fps}fps",
            )
            return chosen
        }
        val fallback = videoStreams.maxByOrNull { it.height } ?: return null
        Log.d(
            "VideoStream",
            "Fallback: ${fallback.format?.mimeType}, ${fallback.codec}, ${fallback.width}x${fallback.height}, ${fallback.quality}, " +
                "${fallback.bitrate}, ${fallback.fps}fps",
        )
        return fallback
    }
}
