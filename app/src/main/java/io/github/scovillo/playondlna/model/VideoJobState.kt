package io.github.scovillo.playondlna.model

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

class VideoJobState {
    private val _progress = mutableFloatStateOf(0f)
    private val _status = mutableStateOf(VideoJobStatus.IDLE)

    val progress: State<Float> get() = _progress
    val status: State<VideoJobStatus> get() = _status

    fun preparing() {
        _status.value = VideoJobStatus.PREPARING
        updateProgress(0f)
    }

    fun finalizing() {
        updateProgress(0f)
        _status.value = VideoJobStatus.FINALIZING
    }

    fun ready() {
        updateProgress(100.0f)
        _status.value = VideoJobStatus.READY
    }

    fun error() {
        _status.value = VideoJobStatus.ERROR
    }

    fun updateProgress(value: Float) {
        _progress.floatValue = value.coerceIn(0.0f, 100.0f)
    }
}