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
    youtubeQuality: String,
    val dlnaProfile: String
) {
    P360("360p", 640, 360, "medium", "AVC_MP4_BL_CIF25_AAC"),
    P480("480p", 854, 480, "large", "AVC_MP4_BL_SD_25_AAC"),
    P720("720p", 1280, 720, "hd720", "AVC_MP4_BL_HD_720p_AAC"),
    P1080("1080p", 1920, 1080, "hd1080", "AVC_MP4_HP_1080p_AAC"),
    P1440("1440p", 2560, 1440, "hd1440", "AVC_MP4_HP_1440p_AAC"),
    P2160("2160p", 3840, 2160, "hd2160", "AVC_MP4_HP_2160p_AAC")
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