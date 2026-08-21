package io.github.scovillo.playondlna.persistence

import android.util.Log
import io.github.scovillo.playondlna.model.Playlist
import org.json.JSONArray
import java.io.File
import java.util.UUID

class PlaylistManager(private val cacheDir: File) {
    private val file = File(cacheDir, "playlists.json")

    @Synchronized
    fun getPlaylists(): List<Playlist> = readPlaylists()

    @Synchronized
    fun createPlaylist(name: String): Playlist? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return null
        val playlist = Playlist(UUID.randomUUID().toString(), normalizedName, emptyList())
        val playlists = readPlaylists() + playlist
        return playlist.takeIf { savePlaylists(playlists) }
    }

    @Synchronized
    fun renamePlaylist(
        id: String,
        name: String,
    ): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return false
        val playlists = readPlaylists()
        if (playlists.none { it.id == id }) return false
        return savePlaylists(playlists.map { if (it.id == id) it.copy(name = normalizedName) else it })
    }

    @Synchronized
    fun deletePlaylist(id: String): Boolean {
        val playlists = readPlaylists()
        val remaining = playlists.filterNot { it.id == id }
        return remaining.size != playlists.size && savePlaylists(remaining)
    }

    @Synchronized
    fun addVideo(
        id: String,
        videoId: String,
    ): Boolean =
        updatePlaylist(id) { playlist ->
            if (videoId.isBlank() || videoId in playlist.videoIds) playlist else playlist.copy(videoIds = playlist.videoIds + videoId)
        }

    @Synchronized
    fun removeVideo(
        id: String,
        videoId: String,
    ): Boolean =
        updatePlaylist(id) { playlist ->
            playlist.copy(videoIds = playlist.videoIds.filterNot { it == videoId })
        }

    private fun updatePlaylist(
        id: String,
        transform: (Playlist) -> Playlist,
    ): Boolean {
        val playlists = readPlaylists()
        val old = playlists.find { it.id == id } ?: return false
        val updated = transform(old)
        return updated != old && savePlaylists(playlists.map { if (it.id == id) updated else it })
    }

    private fun readPlaylists(): List<Playlist> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    runCatching { Playlist.fromJson(value) }.getOrNull()?.let(::add)
                }
            }.distinctBy { it.id }
        } catch (e: Exception) {
            logError("Failed to load playlists", e)
            emptyList()
        }
    }

    private fun savePlaylists(playlists: List<Playlist>): Boolean =
        try {
            cacheDir.mkdirs()
            val temporaryFile = File(cacheDir, "playlists.json.tmp")
            temporaryFile.writeText(JSONArray(playlists.map { it.toJson() }).toString())
            if (!temporaryFile.renameTo(file)) {
                temporaryFile.copyTo(file, overwrite = true)
                temporaryFile.delete()
            }
            true
        } catch (e: Exception) {
            logError("Failed to save playlists", e)
            false
        }

    private fun logError(
        message: String,
        error: Exception,
    ) {
        runCatching { Log.e("PlaylistManager", message, error) }
    }
}
