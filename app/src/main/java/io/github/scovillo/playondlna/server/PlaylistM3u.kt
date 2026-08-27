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
    startIndex: Int = 0,
): PlaylistM3u? {
    val itemsById = libraryItems.associateBy { it.metadata.id }
    val allEntries = playlist.videoIds.mapNotNull(itemsById::get)
    val entries = allEntries.drop(startIndex.coerceIn(0, allEntries.lastIndex.coerceAtLeast(0)))
    if (entries.isEmpty()) return null
    val content =
        buildString {
            appendLine("#EXTM3U")
            entries.forEach { item ->
                val coverAttribute =
                    if (item.thumbnailFile != null) " tvg-logo=\"$baseUrl/${item.metadata.id}/cover.jpg\"" else ""
                appendLine("#EXTINF:-1$coverAttribute,${item.metadata.title.m3uSafeTitle()}")
                val fileName = if (item.metadata.isAudioOnly) "audio.m4a" else "video.mp4"
                appendLine("$baseUrl/${item.metadata.id}/$fileName")
            }
        }
    return PlaylistM3u(playlist.name, content)
}

private fun String.m3uSafeTitle(): String = replace(Regex("[\\r\\n]+"), " ").trim()
