package io.github.scovillo.playondlna.model

import android.util.Log
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Session
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.persistence.LibraryFileType
import io.github.scovillo.playondlna.persistence.LibraryManager
import io.github.scovillo.playondlna.persistence.PlaylistManager
import io.github.scovillo.playondlna.ui.ToastEvent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class StorageManagement(
    private val cacheDir: File,
    private val libraryDir: File,
    private val libraryManager: LibraryManager,
    private val playlistManager: PlaylistManager,
    private val migration: Deferred<Unit>? = null,
    private val clearSelectedMedia: () -> Unit,
    private val currentVideoFile: State<LibraryItem?>,
    private val currentSession: State<Session?>,
    private val sizeCalculationTrigger: Flow<Any>,
) : ViewModel() {
    init {
        viewModelScope.launch {
            sizeCalculationTrigger.collect {
                AppLog.i("StorageManagement", "Storage changed, recalculating sizes")
                calculateSizes()
            }
        }
        viewModelScope.launch {
            calculateSizes()
        }
    }

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _sizeInGb = MutableStateFlow(0.0)
    private val _sizeInBytes = MutableStateFlow(0L)
    val sizeInBytes = _sizeInBytes.asStateFlow()

    private val _librarySizeInGb = MutableStateFlow(0.0)
    private val _librarySizeInBytes = MutableStateFlow(0L)
    val librarySizeInBytes = _librarySizeInBytes.asStateFlow()

    private fun calculateSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            migration?.join()
            val cacheSize = calculateCacheSize(cacheDir) / (1024.0 * 1024 * 1024)
            val librarySize = calculateLibrarySize() / (1024.0 * 1024 * 1024)
            AppLog.i("StorageManagement", "Cache size = $cacheSize GB, library size = $librarySize GB")
            _sizeInBytes.value = calculateCacheSize(cacheDir)
            _librarySizeInBytes.value = calculateLibrarySize()
            _sizeInGb.value = cacheSize
            _librarySizeInGb.value = librarySize
        }
    }

    fun clearLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryFiles = libraryDir.listFiles()?.filter { LibraryFileType.from(it) != null }.orEmpty()
            var success = true
            libraryFiles.forEach { file ->
                if (file.exists() && !file.delete()) {
                    Log.e("StorageManagement", "Failed to delete library file ${file.name}")
                    success = false
                }
            }
            if (success) clearSelectedMedia()
            _toastEvents.emit(ToastEvent.Show(if (success) R.string.library_cleared else R.string.library_clear_failed))
            calculateSizes()
        }
    }

    fun purgeUnusedVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val referencedIds = playlistManager.getPlaylists().flatMap { it.videoIds }.toSet()
            val unusedItems = libraryManager.fetchAllItems().filter { it.metadata.id !in referencedIds }
            val selectedItemIsUnused = unusedItems.any { it.metadata.id == currentVideoFile.value?.metadata?.id }
            var success = true
            unusedItems.forEach { item ->
                if (!libraryManager.deleteItem(item)) success = false
            }
            if (success && selectedItemIsUnused) clearSelectedMedia()
            Log.i("StorageManagement", "Purged ${unusedItems.size} unused library items")
            _toastEvents.emit(ToastEvent.Show(if (success) R.string.unused_videos_purged else R.string.unused_videos_purge_failed))
            calculateSizes()
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!cacheDir.exists()) {
                return@launch
            }
            val runningSessions = FFmpegKit.listSessions()
            val currentSession = currentSession.value
            runningSessions.forEach {
                if (currentSession == null || it.sessionId != currentSession.sessionId) {
                    Log.i("ClearCache", "Cancel FFmpegKit with id ${it.sessionId}")
                    FFmpegKit.cancel(it.sessionId)
                }
            }
            val currentItem = currentVideoFile.value
            cacheDir.listFiles()?.forEach { file ->
                if (file.exists() && (
                        currentItem == null ||
                            !file.name.contains(
                                currentItem.metadata.id,
                            )
                        )
                ) {
                    file.delete()
                }
            }
            _toastEvents.emit(ToastEvent.Show(R.string.cache_cleared))
            calculateSizes()
        }
    }

    private fun calculateCacheSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { calculateCacheSize(it) } ?: 0
    }

    private fun calculateLibrarySize(): Long =
        libraryDir.listFiles()
            ?.filter { LibraryFileType.from(it) != null }
            ?.sumOf { it.length() }
            ?: 0L
}
