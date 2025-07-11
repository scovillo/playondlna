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

package de.scovillo.playondlna

class YoutubeDlFileName(private val rootDir: String, private val fileExtension: String) {

    fun extract(expression: String): String? {
        return this.extractBetween(expression, this.rootDir, this.fileExtension)
    }

    private fun extractBetween(input: String, start: String, end: String): String? {
        val regex = Regex("(?<=${Regex.escape(start)}).*?${Regex.escape(end)}")
        return regex.find(input)?.value
    }
}