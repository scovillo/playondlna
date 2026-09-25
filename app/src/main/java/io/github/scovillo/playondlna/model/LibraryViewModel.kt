package io.github.scovillo.playondlna.model

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.persistence.LibraryManager
import io.github.scovillo.playondlna.persistence.PlaylistManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val libraryManager: LibraryManager,
    private val playlistManager: PlaylistManager,
    private val migration: Deferred<Unit>? = null,
    migrationProgress: StateFlow<Float>? = null,
) : ViewModel() {
    private val _items = mutableStateOf<List<LibraryItem>>(emptyList())
    val items: State<List<LibraryItem>> = _items

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _isMigrating = mutableStateOf(false)
    val isMigrating: State<Boolean> = _isMigrating

    private val _migrationProgress = mutableStateOf(0f)
    val migrationProgress: State<Float> = _migrationProgress

    init {
        viewModelScope.launch {
            migrationProgress?.collect { _migrationProgress.value = it }
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            _isMigrating.value = migration != null && !migration.isCompleted
            try {
                migration?.join()
                val result =
                    withContext(Dispatchers.IO) {
                        libraryManager.fetchAllItems()
                    }
                _items.value = result
            } finally {
                _isMigrating.value = false
                _isLoading.value = false
            }
        }
    }

    fun deleteItem(
        item: LibraryItem,
        playlistIds: List<String>,
        onFinished: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val deleted =
                withContext(Dispatchers.IO) {
                    libraryManager.deleteItem(item).also { success ->
                        if (success) {
                            playlistIds.forEach { playlistId -> playlistManager.removeVideo(playlistId, item.metadata.id) }
                        }
                    }
                }
            onFinished(deleted)
        }
    }
}
