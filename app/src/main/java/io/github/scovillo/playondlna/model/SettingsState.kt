package io.github.scovillo.playondlna.model

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class VideoQuality(
    val title: String,
    val width: Int,
    val height: Int,
    youtubeQuality: String
) {
    P360("360p", 640, 360, "medium"),
    P480("480p", 854, 480, "large"),
    P720("720p", 1280, 720, "hd720"),
    P1080("1080p", 1920, 1080, "hd1080"),
    P1440("1440p", 2560, 1440, "hd1440"),
    P2160("2160p", 3840, 2160, "hd2160")
}

class SettingsState(private val repository: SettingsRepository, val onClearCache: () -> Unit) :
    ViewModel() {
    private val _videoQuality = mutableStateOf(VideoQuality.P720)
    val videoQuality: State<VideoQuality> get() = _videoQuality

    init {
        viewModelScope.launch {
            val lastQuality = repository.videoQualityFlow.first()
            _videoQuality.value = lastQuality
        }
    }

    fun onVideoQualitySelect(value: VideoQuality) {
        _videoQuality.value = value
        viewModelScope.launch {
            repository.saveVideoQuality(value)
        }
    }
}