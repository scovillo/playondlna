package io.github.scovillo.playondlna.model

import android.util.Log
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Session
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.stream.VideoFile
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CacheControl(
    private val cacheDir: File,
    private val currentVideoFile: State<VideoFile?>,
    private val currentSession: State<Session?>,
    private val sizeCalculationTrigger: Flow<Any>
) : ViewModel() {

    init {
        viewModelScope.launch {
            sizeCalculationTrigger.collect {
                AppLog.i("CacheControl", "Ffmpeg Session completed, recalculating cache size")
                calculateCacheDirSizeInGb()
            }
        }
        viewModelScope.launch {
            calculateCacheDirSizeInGb()
        }
    }

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _sizeInGb = MutableStateFlow(0.0)
    val sizeInGb = _sizeInGb.asStateFlow()

    fun calculateCacheDirSizeInGb() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = calculateCacheSize(cacheDir) / (1024.0 * 1024 * 1024)
            AppLog.i("CacheControl", "Cache size = $size GB")
            _sizeInGb.value = size
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!cacheDir.exists())
                return@launch
            val runningSessions = FFmpegKit.listSessions()
            val currentSession = currentSession.value
            runningSessions.forEach {
                if (currentSession == null || it.sessionId != currentSession.sessionId) {
                    Log.i("clearCache", "Cancel FFmpegKit with id ${it.sessionId}")
                    FFmpegKit.cancel(it.sessionId)
                }
            }
            val currentVideoFile = currentVideoFile.value
            cacheDir.listFiles()?.forEach { file ->
                if (file.exists() && (currentVideoFile == null || !file.name.contains(
                        currentVideoFile.id
                    ))
                ) {
                    file.delete()
                }
            }
            _toastEvents.emit(ToastEvent.Show(R.string.cache_cleared))
            calculateCacheDirSizeInGb()
        }
    }

    private fun calculateCacheSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { calculateCacheSize(it) } ?: 0
    }
}