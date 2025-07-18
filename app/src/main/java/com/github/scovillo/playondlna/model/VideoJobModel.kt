package com.github.scovillo.playondlna.model

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import com.github.scovillo.playondlna.stream.VideoFileInfo
import com.github.scovillo.playondlna.stream.videoHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import java.io.File

enum class VideoJobStatus { IDLE, PREPARING, READY, ERROR }

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
            try {
                _currentVideoFileInfo.value = null
                _currentSession.value = null
                _status.value = VideoJobStatus.PREPARING
                _progress.floatValue = 0f
                _title.value = url
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                _title.value = extractor.name
                val bestVideo = extractor.videoStreams.maxByOrNull { it.height }
                val bestAudio = extractor.audioStreams.maxByOrNull { it.averageBitrate }
                if (bestVideo == null || bestAudio == null) {
                    _status.value = VideoJobStatus.ERROR
                    throw IllegalStateException("Streams not found")
                }
                val tempFile = File.createTempFile("${extractor.id}_muxed", ".mp4", cacheDir)
                val ffmpegCmd = listOf(
                    "-i", bestVideo.content,
                    "-i", bestAudio.content,
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-movflags +frag_keyframe+empty_moov+default_base_moof",
                    "-shortest",
                    "-y",
                    tempFile.absolutePath
                )
                videoHttpServer.allFiles[extractor.id] = tempFile
                _currentSession.value = FFmpegKit.executeAsync(
                    ffmpegCmd.joinToString(" "),
                    { session ->
                        if (ReturnCode.isSuccess(session.returnCode)) {
                            Log.d("Mux", "Muxing completed successfully")
                        } else {
                            Log.e("Mux", "Muxing failed")
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
                        _progress.floatValue =
                            (rawProgress * (100f / 5f)).coerceAtMost(100.0).toFloat()
                        if (progress.value == 100.0f) {
                            _currentVideoFileInfo.value = VideoFileInfo(extractor)
                            _status.value = VideoJobStatus.READY
                        }
                        Log.d("FFmpegProgress", "Progress: $rawProgress%")
                    }
                )
            } catch (e: Exception) {
                _status.value = VideoJobStatus.ERROR
                e.printStackTrace()
            }
        }
    }
}