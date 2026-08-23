package io.github.scovillo.playondlna.model

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.persistence.PlaylistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistViewModel(private val playlistManager: PlaylistManager) : ViewModel() {
    private val _playlists = mutableStateOf<List<Playlist>>(emptyList())
    val playlists: State<List<Playlist>> = _playlists

    fun loadPlaylists() = run { _playlists.value = playlistManager.getPlaylists() }

    fun createPlaylist(name: String) = mutate { playlistManager.createPlaylist(name) != null }

    fun renamePlaylist(
        id: String,
        name: String,
    ) = mutate { playlistManager.renamePlaylist(id, name) }

    fun deletePlaylist(id: String) = mutate { playlistManager.deletePlaylist(id) }

    fun addVideo(
        id: String,
        videoId: String,
    ) = mutate { playlistManager.addVideo(id, videoId) }

    fun removeVideo(
        id: String,
        videoId: String,
    ) = mutate { playlistManager.removeVideo(id, videoId) }

    private fun mutate(operation: () -> Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { operation() }
            loadPlaylists()
        }
    }
}
