package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.persistence.LibraryItem

data class PlaylistM3u(
    val title: String,
    val content: String,
)

fun createPlaylistM3u(
    playlist: Playlist,
    libraryItems: List<LibraryItem>,
    baseUrl: String,
): PlaylistM3u? {
    val itemsById = libraryItems.associateBy { it.metadata.id }
    val entries = playlist.videoIds.mapNotNull(itemsById::get)
    if (entries.isEmpty()) return null
    val content =
        buildString {
            appendLine("#EXTM3U")
            entries.forEach { item ->
                appendLine("#EXTINF:-1,${item.metadata.title.m3uSafeTitle()}")
                appendLine("$baseUrl/${item.metadata.id}/video.mp4")
            }
        }
    return PlaylistM3u(playlist.name, content)
}

private fun String.m3uSafeTitle(): String = replace(Regex("[\\r\\n]+"), " ").trim()
