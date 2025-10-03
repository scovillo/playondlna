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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.stream.VideoFile
import io.github.scovillo.playondlna.stream.videoHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

fun StreamExtractor.bestVideoStream(quality: VideoQuality): VideoStream? {
    videoOnlyStreams.forEach {
        Log.d(
            "VideoStream",
            "Available: ${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}"
        )
    }
    val compatibleStreams = videoOnlyStreams.filter { it.hasBestCompatibility }
    compatibleStreams.forEach {
        Log.d(
            "VideoStream",
            "compatibleStreams: ${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}"
        )
    }
    val compatibleStreamsWithPreferredQuality = compatibleStreams.sortedByDescending { it.height }
        .filter { it.height <= quality.height }
    compatibleStreamsWithPreferredQuality.forEach {
        Log.d(
            "VideoStream",
            "compatibleStreamsWithPreferredQuality: ${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}"
        )
    }
    if (compatibleStreamsWithPreferredQuality.isNotEmpty()) {
        val chosen = compatibleStreamsWithPreferredQuality.maxBy { it.height }
        Log.d(
            "VideoStream",
            "Chosen: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.width}x${chosen.height}, ${chosen.quality}, ${chosen.bitrate}, ${chosen.fps}fps"
        )
        return chosen
    }
    if (compatibleStreams.isNotEmpty()) {
        val chosen = compatibleStreams.maxBy { it.height }
        Log.d(
            "VideoStream",
            "Chosen without quality setting: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.width}x${chosen.height}, ${chosen.quality}, ${chosen.bitrate}, ${chosen.fps}fps"
        )
        return chosen
    }
    val fallback = videoOnlyStreams.maxByOrNull { it.height }
    Log.d(
        "VideoStream",
        "Fallback: ${fallback?.format?.mimeType}, ${fallback?.codec}, ${fallback?.width}x${fallback?.height}, ${fallback?.quality}, ${fallback?.bitrate}, ${fallback?.fps}fps"
    )
    return fallback
}

fun StreamExtractor.bestAudioStream(): AudioStream? {
    audioStreams.forEach {
        Log.i(
            "AudioStream",
            "Available: ${it.format?.mimeType}, ${it.codec}, ${it.audioLocale}"
        )
    }
    val compatibleStreams = audioStreams.filter { it.hasBestCompatibility }
    if (compatibleStreams.isNotEmpty()) {
        val chosen = compatibleStreams.maxBy { it.averageBitrate }
        Log.d(
            "AudioStream",
            "Chosen: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.quality}, ${chosen.bitrate}"
        )
        return chosen
    }
    val fallback = audioStreams.maxByOrNull { it.averageBitrate }
    Log.d(
        "AudioStream",
        "Fallback: ${fallback?.format?.mimeType}, ${fallback?.codec}, ${fallback?.quality}, ${fallback?.bitrate}"
    )
    return fallback
}

class VideoJobModel(settingsRepository: SettingsRepository) : ViewModel() {
    private var _currentVideoFile = mutableStateOf<VideoFile?>(null)
    private var _currentSession = mutableStateOf<Session?>(null)
    private val _title = mutableStateOf("idle")
    private val state = VideoJobState()
    private val videoQuality: StateFlow<VideoQuality> = settingsRepository.videoQualityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = VideoQuality.P720
        )

    val currentVideoFile: State<VideoFile?> get() = _currentVideoFile
    val currentSession: State<Session?> get() = _currentSession
    val title: State<String> get() = _title
    val progress: State<Float> get() = state.progress
    val status: State<VideoJobStatus> get() = state.status

    fun prepareVideo(url: String, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i("VideoJobModel", "Requesting: $url")
            _currentVideoFile.value = null
            _currentSession.value = null
            _title.value = url
            try {
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                _title.value = extractor.name
                val cachedFile = cacheDir.listFiles()
                    ?.find { it.exists() && it.name.contains(extractor.id) && it.name.contains("final") }
                if (cachedFile != null) {
                    Log.i(
                        "VideoJobModel",
                        "Loading ${extractor.id} from cache"
                    )
                    videoHttpServer.allFiles[extractor.id] =
                        VideoFile(extractor, cachedFile, videoQuality.value)
                    _currentVideoFile.value = videoHttpServer.allFiles[extractor.id]
                    state.ready()
                    Log.d("VideoFile", "Available under ${_currentVideoFile.value!!.url}")
                } else {
                    startMuxing(extractor, cacheDir)
                }
            } catch (e: Exception) {
                state.error()
                e.printStackTrace()
            }
        }
    }

    private fun startMuxing(extractor: StreamExtractor, cacheDir: File) {
        state.preparing()
        Log.d("Setting VideoQuality", "Current: ${videoQuality.value.title}")
        val bestVideo = extractor.bestVideoStream(videoQuality.value)
        if (bestVideo == null) {
            state.error()
            throw IllegalStateException("Video stream not found")
        }
        val bestAudio = extractor.bestAudioStream()
        if (bestAudio == null) {
            state.error()
            throw IllegalStateException("Audio stream not found")
        }
        val fragmentedFile =
            File.createTempFile("${extractor.id}_muxed_fragmented", ".mp4", cacheDir)
        val ffmpegCmd = mutableListOf(
            "-protocol_whitelist file,http,https,tcp,tls",
            "-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5",
            "-user_agent \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36\"",
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
                "-movflags", "+frag_keyframe+empty_moov+faststart",
                "-shortest",
                "-y",
                fragmentedFile.absolutePath
            )
        )
        Log.i("VideoJobModel", "Final FFMPEGKit command: ${ffmpegCmd.joinToString(" ")}")
        videoHttpServer.allFiles[extractor.id] =
            VideoFile(extractor, fragmentedFile, videoQuality.value)
        Log.d("Mux", "Start fragmented muxing ${extractor.id}")
        _currentSession.value = FFmpegKit.executeAsync(
            ffmpegCmd.joinToString(" "),
            { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    Log.d("Mux", "Fragmented muxing completed successfully")
                    if (currentSession.value?.sessionId == session.sessionId) {
                        finalizeMuxing(extractor, fragmentedFile, cacheDir)
                    }
                } else {
                    Log.e("Mux", "Fragmented muxing failed")
                    state.error()
                    fragmentedFile.delete()
                }
            },
            { log -> Log.d("Mux", log.message) },
            { statistics ->
                if (statistics.sessionId != _currentSession.value?.sessionId) {
                    return@executeAsync
                }
                val videoDurationInMs = extractor.length * 1000
                val rawProgress = if (videoDurationInMs > 0) {
                    (statistics.time * 100 / videoDurationInMs).toFloat()
                } else 0.0f
                val playableProgress = rawProgress * (100f / 10f)
                if (status.value == VideoJobStatus.PREPARING) {
                    state.updateProgress(playableProgress)
                } else {
                    state.updateProgress(rawProgress)
                }
                if (status.value != VideoJobStatus.PLAYABLE && state.progress.value == 100.0f) {
                    _currentVideoFile.value = videoHttpServer.allFiles[extractor.id]
                    state.playable()
                }
                Log.d("FFmpegProgress", "Progress: $rawProgress%")
            }
        )
    }

    private fun finalizeMuxing(extractor: StreamExtractor, sourceFile: File, cacheDir: File) {
        state.finalizing()
        val tempFile = File.createTempFile("${extractor.id}_muxed_temp", ".mp4", cacheDir)
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
                    val finalFile =
                        File(tempFile.parentFile, tempFile.name.replace("_temp", "_final"))
                    tempFile.renameTo(finalFile)
                    videoHttpServer.allFiles[extractor.id] =
                        VideoFile(extractor, finalFile, videoQuality.value)
                    _currentVideoFile.value = videoHttpServer.allFiles[extractor.id]
                    state.ready()
                    Log.d("VideoFile", "Available under ${_currentVideoFile.value!!.url}")
                    viewModelScope.launch(Dispatchers.IO) {
                        val durationInMs = videoHttpServer.allFiles[extractor.id]!!.durationInMs
                        Log.d(
                            "VideoFile",
                            "Scheduled for deletion (in ${durationInMs / 1000}s): ${sourceFile.name}"
                        )
                        delay(durationInMs)
                        if (sourceFile.exists()) {
                            val deleted = sourceFile.delete()
                            Log.d(
                                "VideoFile",
                                if (deleted) "🧹 Deleted ${sourceFile.name}"
                                else "⚠️ Could not delete ${sourceFile.name} (still in use?)"
                            )
                        }
                    }

                } else {
                    Log.e("Mux", "Final muxing failed")
                    state.error()
                    tempFile.delete()
                }
            },
            { log -> Log.d("Mux", log.message) },
            { statistics ->
                if (statistics.sessionId != _currentSession.value?.sessionId) {
                    return@executeAsync
                }
                val videoDurationInMs = extractor.length * 1000
                val rawProgress = if (videoDurationInMs > 0) {
                    statistics.time * 100 / videoDurationInMs
                } else 0.0f
                state.updateProgress(rawProgress.toFloat())
                Log.d("FFmpegProgress", "Progress: $rawProgress%")
            }
        )
    }
}