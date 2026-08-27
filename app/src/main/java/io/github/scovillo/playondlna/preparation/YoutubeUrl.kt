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

import java.net.URI

internal class YoutubeUrl {
    fun normalize(sharedText: String): String {
        val url =
            Regex("https?://[^\\s)\\]]+")
                .find(sharedText)
                ?.value
                ?.replace("\\&", "&")
                ?: sharedText.trim()

        return yMusicVideoId(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url
    }

    private fun yMusicVideoId(url: String): String? =
        runCatching {
            val uri = URI(url)
            if (uri.host.equals(YMUSIC_HOST, ignoreCase = true) && uri.path == WATCH_PATH) {
                uri.rawQuery
                    ?.split('&')
                    ?.map { it.split('=', limit = 2) }
                    ?.firstOrNull { it.size == 2 && it[0] == VIDEO_ID_PARAMETER }
                    ?.get(1)
                    ?.takeIf { it.matches(YOUTUBE_VIDEO_ID) }
            } else {
                null
            }
        }.getOrNull()

    private companion object {
        const val YMUSIC_HOST = "ymusicapp.com"
        const val WATCH_PATH = "/watch"
        const val VIDEO_ID_PARAMETER = "v"
        val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
