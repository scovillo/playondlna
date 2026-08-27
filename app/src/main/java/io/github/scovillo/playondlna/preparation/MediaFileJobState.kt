package io.github.scovillo.playondlna.preparation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

class MediaFileJobState {
    private val _progress = mutableFloatStateOf(0f)
    private val _status = mutableStateOf(MediaFileJobStatus.IDLE)

    val progress: State<Float> get() = _progress
    val status: State<MediaFileJobStatus> get() = _status

    fun preparing() {
        _status.value = MediaFileJobStatus.PREPARING
        updateProgress(0f)
    }

    fun finalizing() {
        updateProgress(50f)
        _status.value = MediaFileJobStatus.FINALIZING
    }

    fun ready() {
        updateProgress(100.0f)
        _status.value = MediaFileJobStatus.READY
    }

    fun idle() {
        updateProgress(0f)
        _status.value = MediaFileJobStatus.IDLE
    }

    fun error() {
        _status.value = MediaFileJobStatus.ERROR
    }

    fun updateProgress(value: Float) {
        _progress.floatValue = value.coerceIn(0.0f, 100.0f)
    }

    fun updateDownloadProgress(value: Float) = updateProgress(value / 2f)

    fun updateFinalizingProgress(value: Float) = updateProgress(50f + value / 2f)
}
