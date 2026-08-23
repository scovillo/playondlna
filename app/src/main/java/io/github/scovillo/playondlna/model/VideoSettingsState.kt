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

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class VideoQuality(
    val titleRes: Int,
    val height: Int,
    val dlnaProfile: String,
) {
    P360(R.string.q_360p, 360, "AVC_MP4_BL_CIF25_AAC"),
    P480(R.string.q_480p, 480, "AVC_MP4_BL_SD_25_AAC"),
    P720(R.string.q_720p, 720, "AVC_MP4_BL_HD_720p_AAC"),
    P1080(R.string.q_1080p, 1080, "AVC_MP4_HP_1080p_AAC"),
    P1440(R.string.q_1440p, 1440, "AVC_MP4_HP_1440p_AAC"),
    P2160(R.string.q_2160p, 2160, "AVC_MP4_HP_2160p_AAC"),
}

class VideoSettingsState(private val repository: SettingsRepository) : ViewModel() {
    private val _videoQuality = mutableStateOf(VideoQuality.P720)
    private val _isSubtitleEnabled = mutableStateOf(false)
    private val _isInternalSubtitleEnabled = mutableStateOf(false)
    private val _isWlanProtectionEnabled = mutableStateOf(true)
    val videoQuality: State<VideoQuality> get() = _videoQuality
    val isSubtitleEnabled: State<Boolean> get() = _isSubtitleEnabled
    val isInternalSubtitleEnabled: State<Boolean> get() = _isInternalSubtitleEnabled
    val isWlanProtectionEnabled: State<Boolean> get() = _isWlanProtectionEnabled

    init {
        viewModelScope.launch {
            _videoQuality.value = repository.videoQualityFlow.first()
            _isSubtitleEnabled.value = repository.isSubtitleEnabledFlow.first()
            _isInternalSubtitleEnabled.value = repository.isInternalSubtitleEnabledFlow.first()
            _isWlanProtectionEnabled.value = repository.isWlanProtectionEnabledFlow.first()
        }
    }

    fun onVideoQualitySelect(value: VideoQuality) {
        _videoQuality.value = value
        viewModelScope.launch {
            repository.saveVideoQuality(value)
        }
    }

    fun onSubtitleEnabledSelect(value: Boolean) {
        _isSubtitleEnabled.value = value
        viewModelScope.launch {
            repository.saveSubtitleEnabled(value)
        }
    }

    fun onSubtitleInternalEnabledSelect(value: Boolean) {
        _isInternalSubtitleEnabled.value = value
        viewModelScope.launch {
            repository.saveInternalSubtitleEnabled(value)
        }
    }

    fun onWlanProtectionEnabledSelect(value: Boolean) {
        _isWlanProtectionEnabled.value = value
        viewModelScope.launch {
            repository.saveWlanProtectionEnabled(value)
        }
    }
}
