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
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.stream.PlayOnDlnaStreamDownload
import io.github.scovillo.playondlna.stream.VideoFile
import io.github.scovillo.playondlna.stream.WifiConnectionState
import io.github.scovillo.playondlna.stream.videoHttpServer
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.util.Collections
import java.util.Locale

enum class VideoJobStatus { IDLE, PREPARING, FINALIZING, READY, ERROR }

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
    Log.d(
        "VideoStreams",
        videoOnlyStreams.joinToString(System.lineSeparator()) { "${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}" }
    )
    val compatibleVideoStreams = videoOnlyStreams.filter { it.hasBestCompatibility }
    Log.d(
        "compatibleVideoStreams",
        compatibleVideoStreams.joinToString(System.lineSeparator()) { "${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}" }
    )
    val compatibleVideoStreamsWithPreferredQuality =
        compatibleVideoStreams.sortedByDescending { it.height }
            .filter { it.height <= quality.height }
    Log.d(
        "compatibleVideoStreamsWithPreferredQuality",
        compatibleVideoStreamsWithPreferredQuality.joinToString(System.lineSeparator()) { "${it.format?.mimeType}, ${it.codec}, ${it.width}x${it.height}, ${it.quality}, ${it.bitrate}, ${it.fps}" }
    )
    if (compatibleVideoStreamsWithPreferredQuality.isNotEmpty()) {
        val chosen = compatibleVideoStreamsWithPreferredQuality.maxBy { it.height }
        Log.d(
            "VideoStream",
            "Chosen: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.width}x${chosen.height}, ${chosen.quality}, ${chosen.bitrate}, ${chosen.fps}fps"
        )
        return chosen
    }
    if (compatibleVideoStreams.isNotEmpty()) {
        val chosen = compatibleVideoStreams.maxBy { it.height }
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
    Log.i(
        "AudioStreams",
        audioStreams.joinToString(System.lineSeparator()) { "${it.format?.mimeType}, ${it.codec}, ${it.bitrate}, ${it.audioLocale}" }
    )
    val locale = Locale.getDefault()
    Log.d("AudioStream", "System language: ${locale.language}")
    val compatibleStreams = audioStreams.filter { it.hasBestCompatibility }
    if (compatibleStreams.isNotEmpty()) {
        val chosen = compatibleStreams.filter { it.audioLocale?.language === locale.language }
            .maxByOrNull { it.averageBitrate } ?: compatibleStreams.maxBy { it.averageBitrate }
        Log.d(
            "AudioStream",
            "Chosen: ${chosen.format?.mimeType}, ${chosen.codec}, ${chosen.quality}, ${chosen.bitrate}, ${chosen?.audioLocale}"
        )
        return chosen
    }
    val fallback = audioStreams.filter { it.audioLocale?.language === locale.language }
        .maxByOrNull { it.averageBitrate } ?: audioStreams.maxByOrNull { it.averageBitrate }
    Log.d(
        "AudioStream",
        "Fallback: ${fallback?.format?.mimeType}, ${fallback?.codec}, ${fallback?.quality}, ${fallback?.bitrate}, ${fallback?.audioLocale}"
    )
    return fallback
}

class VideoJobModel(
    settingsRepository: SettingsRepository,
    private val wifiConnectionState: WifiConnectionState
) : ViewModel() {

    private var _currentVideoFile = mutableStateOf<VideoFile?>(null)
    private var _currentSession = mutableStateOf<Session?>(null)
    private val _title = mutableStateOf("idle")
    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    private val state = VideoJobState()

    private val videoQuality: StateFlow<VideoQuality> = settingsRepository.videoQualityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = VideoQuality.P720
        )
    private val runningJobs = Collections.synchronizedList(mutableListOf<Job>())

    val currentVideoFile: State<VideoFile?> get() = _currentVideoFile
    val currentSession: State<Session?> get() = _currentSession
    val title: State<String> get() = _title
    val progress: State<Float> get() = state.progress
    val status: State<VideoJobStatus> get() = state.status
    val toastEvents = _toastEvents.asSharedFlow()

    init {
        this.monitorWifiConnection()
    }

    fun prepareVideo(url: String, cacheDir: File) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i("VideoJobModel", "Requesting: $url")
                _currentVideoFile.value = null
                _currentSession.value = null
                _title.value = url
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                _title.value = extractor.name
                val cachedFile = cacheDir.listFiles()
                    ?.find { it.exists() && it.name.contains(extractor.id) && it.name.contains("final") }
                if (cachedFile != null) {
                    Log.i("VideoJobModel", "Loading ${extractor.id} from cache")
                    videoHttpServer.allFiles[extractor.id] =
                        VideoFile(extractor, cachedFile, videoQuality.value)
                    _currentVideoFile.value = videoHttpServer.allFiles[extractor.id]
                    state.ready()
                    Log.d("VideoFile", "Available under ${_currentVideoFile.value!!.url}")
                } else {
                    mux(extractor, cacheDir)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ContentNotAvailableException) {
                _toastEvents.emit(ToastEvent.ShowPlain(e.message ?: "Error loading video"))
                state.error()
            } catch (e: Exception) {
                Log.e("VideoJobModel", "Error in job for $url", e)
                state.error()
            }
        }
        job.invokeOnCompletion { cause ->
            runningJobs.remove(job)
            when (cause) {
                null -> Log.d("VideoJobModel", "Job for $url completed successfully")
                is CancellationException -> Log.w("VideoJobModel", "Job for $url was cancelled")
                else -> Log.e("VideoJobModel", "Job for $url failed", cause)
            }
        }
        runningJobs.add(job)
    }

    fun cancelJobs() {
        Log.w("VideoJobModel", "Cancelling ${runningJobs.size} running jobs")
        runningJobs.forEach { it.cancel() }
        runningJobs.clear()
    }

    private suspend fun mux(extractor: StreamExtractor, cacheDir: File) {
        if (wifiConnectionState.isConnected()) {
            state.preparing()
        } else {
            state.error()
            return
        }
        val bestVideo = extractor.bestVideoStream(videoQuality.value)
            ?: throw IllegalStateException("Video stream not found")
        val bestAudio = extractor.bestAudioStream()
            ?: throw IllegalStateException("Audio stream not found")
        val streamFiles = PlayOnDlnaStreamDownload(
            extractor.id,
            bestVideo.content,
            bestAudio.content,
            cacheDir,
            state
        ).startDownload()

        val ffmpegCmd = mutableListOf(
            "-i", streamFiles.videoFile.absolutePath,
            "-i", streamFiles.audioFile.absolutePath,
            "-c:v", "copy"
        )
        if (bestAudio.hasBestCompatibility) {
            ffmpegCmd.addAll(listOf("-c:a", "copy"))
        } else {
            ffmpegCmd.addAll(listOf("-c:a", "aac"))
        }
        val muxFile = File.createTempFile("${extractor.id}_muxed_final_", ".mp4", cacheDir)
        ffmpegCmd.addAll(listOf("-movflags", "faststart", "-shortest", "-y", muxFile.absolutePath))

        Log.i("VideoJobModel", "Final FFMPEGKit command: ${ffmpegCmd.joinToString(" ")}")
        state.finalizing()
        _currentSession.value = FFmpegKit.executeAsync(
            ffmpegCmd.joinToString(" "),
            { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    Log.d("Mux", "Muxing completed successfully")
                    if (_currentSession.value?.sessionId == session.sessionId) {
                        videoHttpServer.allFiles[extractor.id] =
                            VideoFile(extractor, muxFile, videoQuality.value)
                        _currentVideoFile.value = videoHttpServer.allFiles[extractor.id]
                        state.ready()
                    }
                } else {
                    Log.e("Mux", "Muxing failed!")
                    state.error()
                    muxFile.delete()
                }
                streamFiles.delete()
            },
            { log -> Log.d("Mux", log.message) },
            { statistics ->
                val videoDurationInMs = extractor.length * 1000
                val rawProgress =
                    if (videoDurationInMs > 0) (statistics.time * 100 / videoDurationInMs).toFloat() else 0.0f
                state.updateProgress(rawProgress)
                Log.d("FFmpegProgress", "Progress: $rawProgress%")
            }
        )
    }

    private fun monitorWifiConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                if (runningJobs.isEmpty()) {
                    continue
                }
                if (!wifiConnectionState.isConnected()) {
                    withContext(Dispatchers.Main) {
                        _toastEvents.emit(ToastEvent.Show(R.string.wlan_disconnected))
                        state.error()
                    }
                    cancelJobs()
                }
            }
        }
    }

}