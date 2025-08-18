/*
 * PlayOnDlna - An Android application to play media on dlna devices
 * Copyright (C) 2025 Lukas Scheerer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.scovillo.playondlna.model

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import io.github.scovillo.playondlna.stream.VideoFileInfo
import io.github.scovillo.playondlna.stream.videoHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File

enum class VideoJobStatus { IDLE, PREPARING, PLAYABLE, FINALIZING, READY, ERROR }

val VideoStream.hasBestCompatibility: Boolean
    get() {
        return format?.mimeType?.startsWith("video/mp4") == true && codec?.startsWith(
            "avc"
        ) == true
    }

val AudioStream.hasBestCompatibility: Boolean
    get() {
        return format?.mimeType?.startsWith("audio/mp4") == true && codec?.startsWith("mp4a") == true
    }

fun StreamExtractor.bestVideoStream(): VideoStream? {
    val compatibleStreams = videoStreams.filter { it.hasBestCompatibility }
    if (compatibleStreams.isNotEmpty()) {
        return compatibleStreams.maxByOrNull { it.height }
    }
    return videoStreams.maxByOrNull { it.height }
}

fun StreamExtractor.bestAudioStream(): AudioStream? {
    val compatibleStreams = audioStreams.filter { it.hasBestCompatibility }
    if (compatibleStreams.isNotEmpty()) {
        return compatibleStreams.maxByOrNull { it.averageBitrate }
    }
    return audioStreams.maxByOrNull { it.averageBitrate }
}

class VideoJobModel() : ViewModel() {
    private var _currentVideoFileInfo = mutableStateOf<VideoFileInfo?>(null)
    private var _currentSession = mutableStateOf<Session?>(null)
    private val _progress = mutableFloatStateOf(0f)
    private val _status = mutableStateOf(VideoJobStatus.IDLE)
    private val _title = mutableStateOf("idle")

    val currentVideoFileInfo: State<VideoFileInfo?> get() = _currentVideoFileInfo
    val currentSession: State<Session?> get() = _currentSession
    val progress: State<Float> get() = _progress
    val title: State<String> get() = _title
    val status: State<VideoJobStatus> get() = _status

    fun prepareVideo(url: String, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i("VideoJobModel", "Requesting: $url")
            _currentVideoFileInfo.value = null
            _currentSession.value = null
            _title.value = url
            try {
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                _title.value = extractor.name
                startMuxing(extractor, cacheDir)
            } catch (e: Exception) {
                _status.value = VideoJobStatus.ERROR
                e.printStackTrace()
            }
        }
    }

    private fun startMuxing(extractor: StreamExtractor, cacheDir: File) {
        _status.value = VideoJobStatus.PREPARING
        _progress.floatValue = 0f
        val bestVideo = extractor.bestVideoStream()
        val bestAudio = extractor.bestAudioStream()
        if (bestVideo == null || bestAudio == null) {
            _status.value = VideoJobStatus.ERROR
            throw IllegalStateException("Streams not found")
        }
        val tempFile =
            File.createTempFile("${extractor.id}_muxed_fragmented", ".mp4", cacheDir)
        val ffmpegCmd = mutableListOf(
            "-i", bestVideo.content,
            "-i", bestAudio.content,
        )
        Log.i(
            "VideoJobModel",
            "Compatible video stream found (format=${bestVideo.format?.mimeType}; codec=${bestVideo.codec})"
        )
        ffmpegCmd.add("-c:v copy")
        if (bestAudio.hasBestCompatibility) {
            Log.i(
                "VideoJobModel",
                "Compatible audio stream found (format=${bestAudio.format?.mimeType}; codec=${bestAudio.codec})"
            )
            ffmpegCmd.add("-c:a copy")
        } else {
            Log.i(
                "VideoJobModel",
                "Converting audio stream (format=${bestAudio.format?.mimeType}; codec=${bestAudio.codec})"
            )
            ffmpegCmd.add("-c:a aac")
        }
        ffmpegCmd.addAll(
            listOf(
                "-movflags", "+frag_keyframe+empty_moov+default_base_moof",
                "-shortest",
                "-y",
                tempFile.absolutePath
            )
        )
        Log.i("VideoJobModel", "Final FFMPEGKit command: ${ffmpegCmd.joinToString(" ")}")
        videoHttpServer.allFiles[extractor.id] = tempFile
        Log.d("Mux", "Start fragmented muxing ${extractor.id}")
        _currentSession.value = FFmpegKit.executeAsync(
            ffmpegCmd.joinToString(" "),
            { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    Log.d("Mux", "Fragmented muxing completed successfully")
                    if (currentSession.value?.sessionId == session.sessionId) {
                        finalizeMuxing(extractor, tempFile, cacheDir)
                    }
                } else {
                    Log.e("Mux", "Fragmented muxing failed")
                    _status.value = VideoJobStatus.ERROR
                }
            },
            { log -> Log.d("Mux", log.message) },
            { statistics ->
                if (statistics.sessionId != _currentSession.value?.sessionId) {
                    return@executeAsync
                }
                val videoDurationInMs = extractor.length * 1000
                val rawProgress = if (videoDurationInMs > 0) {
                    (statistics.time * 100 / videoDurationInMs).coerceIn(0.0, 100.0)
                } else 0.0
                val playableProgress =
                    (rawProgress * (100f / 10f)).coerceAtMost(100.0).toFloat()
                if (status.value == VideoJobStatus.PREPARING) {
                    _progress.floatValue = playableProgress.coerceAtMost(100.0f)
                } else {
                    _progress.floatValue = rawProgress.coerceAtMost(100.0).toFloat()
                }
                if (status.value != VideoJobStatus.PLAYABLE && playableProgress == 100.0f) {
                    _currentVideoFileInfo.value = VideoFileInfo(extractor)
                    _status.value = VideoJobStatus.PLAYABLE
                }
                Log.d("FFmpegProgress", "Progress: $rawProgress%")
            }
        )
    }

    private fun finalizeMuxing(extractor: StreamExtractor, sourceFile: File, cacheDir: File) {
        _status.value = VideoJobStatus.FINALIZING
        val tempFile = File.createTempFile("${extractor.id}_muxed", ".mp4", cacheDir)
        val ffmpegCmd = mutableListOf(
            "-i", sourceFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "copy",
            "-movflags", "faststart",
            "-y",
            tempFile.absolutePath
        )
        Log.d("Mux", "Start final muxing ${extractor.id}")
        _currentSession.value = FFmpegKit.executeAsync(
            ffmpegCmd.joinToString(" "),
            { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    Log.d("Mux", "Final muxing completed successfully")
                    videoHttpServer.allFiles[extractor.id] = tempFile
                    _currentVideoFileInfo.value = VideoFileInfo(extractor)
                    _status.value = VideoJobStatus.READY
                } else {
                    Log.e("Mux", "Final muxing failed")
                    _status.value = VideoJobStatus.ERROR
                }
            },
            { log -> Log.d("Mux", log.message) },
            { statistics ->
                if (statistics.sessionId != _currentSession.value?.sessionId) {
                    return@executeAsync
                }
                val videoDurationInMs = extractor.length * 1000
                val rawProgress = if (videoDurationInMs > 0) {
                    (statistics.time * 100 / videoDurationInMs).coerceIn(0.0, 100.0)
                } else 0.0
                _progress.floatValue = rawProgress.coerceAtMost(100.0).toFloat()
                Log.d("FFmpegProgress", "Progress: $rawProgress%")
            }
        )
    }
}