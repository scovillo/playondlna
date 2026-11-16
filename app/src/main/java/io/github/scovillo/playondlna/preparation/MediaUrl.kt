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

package io.github.scovillo.playondlna.preparation

import android.util.Log
import io.github.scovillo.playondlna.download.okHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

internal class MediaUrl {
    fun normalize(sharedText: String): String {
        val url =
            Regex("https?://[^\\s)\\]]+")
                .find(sharedText)
                ?.value
                ?.replace("\\&", "&")
                ?: sharedText.trim()

        return runCatching {
            val uri = URI(url)
            val youtube = isYoutubeHost(uri.host)
            val yMusic = uri.host.equals("ymusicapp.com", ignoreCase = true) && uri.path == "/watch"
            if (youtube || yMusic) {
                val list = parameter(uri, "list")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                if (list != null && uri.path in listOf("/watch", "/playlist", "/")) {
                    return "https://www.youtube.com/playlist?list=$list"
                }
                if (yMusic) {
                    parameter(uri, "v")?.takeIf { it.matches(VIDEO_ID) }?.let {
                        return "https://www.youtube.com/watch?v=$it"
                    }
                }
            }
            url
        }.getOrDefault(url)
    }

    fun isSoundCloudShortUrl(url: String): Boolean = runCatching { URI(url).host.equals("on.soundcloud.com", ignoreCase = true) }.getOrDefault(false)

    fun resolveSoundCloudShortUrl(url: String): String {
        return try {
            okHttpClient.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                response.request.url.toString().also { resolvedUrl ->
                    Log.i("MediaModel", "Resolved SoundCloud share URL to $resolvedUrl")
                }
            }
        } catch (error: IOException) {
            Log.w("MediaModel", "Could not resolve SoundCloud share URL, using original URL", error)
            url
        }
    }

    fun hasExplicitPlaylistEntries(url: String): Boolean =
        runCatching {
            val uri = URI(url)
            isYoutubeHost(uri.host) && uri.path == "/watch_videos"
        }.getOrDefault(false)

    fun explicitPlaylistEntries(url: String): List<String> =
        runCatching {
            val uri = URI(url)
            require(isYoutubeHost(uri.host) && uri.path == "/watch_videos") { "Not an explicit playlist URL" }
            val ids = parameter(uri, "video_ids")?.split(',') ?: emptyList()
            require(ids.isNotEmpty() && ids.all { it.matches(VIDEO_ID) }) { "Invalid playlist video IDs" }
            ids.distinct().map { "https://www.youtube.com/watch?v=$it" }
        }.getOrElse { throw IllegalArgumentException("Invalid playlist URL", it) }

    private fun parameter(
        uri: URI,
        name: String,
    ): String? =
        uri.rawQuery
            ?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }

    private fun isYoutubeHost(host: String?): Boolean {
        return host != null &&
            (host.equals("youtube.com", ignoreCase = true) || host.endsWith(".youtube.com", ignoreCase = true))
    }

    private companion object {
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
