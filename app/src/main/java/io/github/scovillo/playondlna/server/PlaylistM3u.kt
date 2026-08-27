package io.github.scovillo.playondlna.server

import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.persistence.LibraryItem

data class PlaylistM3u(
    val title: String,
    val content: String,
    val mimeType: String,
)

fun createPlaylistM3u(
    playlist: Playlist,
    libraryItems: List<LibraryItem>,
    baseUrl: String,
): PlaylistM3u? {
    val itemsById = libraryItems.associateBy { it.metadata.id }
    val allEntries = playlist.videoIds.mapNotNull(itemsById::get)
    if (allEntries.isEmpty()) return null
    val mimeType = if (allEntries.all { it.metadata.isAudioOnly }) "video/x-mpegurl; charset=utf-8" else "audio/mpegurl; charset=utf-8"
    val content =
        buildString {
            appendLine("#EXTM3U")
            allEntries.forEach { item ->
                val coverAttribute =
                    if (item.thumbnailFile != null) " tvg-logo=\"$baseUrl/${item.metadata.id}/cover.jpg\"" else ""
                appendLine("#EXTINF:-1$coverAttribute,${item.metadata.title.m3uSafeTitle()}")
                val fileName = if (item.metadata.isAudioOnly) "audio.${item.videoFile.extension.lowercase()}" else "video.mp4"
                appendLine("$baseUrl/${item.metadata.id}/$fileName")
            }
        }
    return PlaylistM3u(playlist.name, content, mimeType)
}

private fun String.m3uSafeTitle(): String = replace(Regex("[\\r\\n]+"), " ").trim()
