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

package io.github.scovillo.playondlna.dlna.soap

import io.github.scovillo.playondlna.dlna.control.TransportState

/**
 * Parses SOAP response XML strings from UPnP devices.
 */
class SoapResponseExtractor {
    /**
     * Extract transport state from GetTransportInfo response.
     */
    fun parseTransportState(responseBody: String): TransportState {
        val value =
            Regex("""<(?:[A-Za-z_][\w.-]*:)?CurrentTransportState>\s*([^<]+)\s*</""")
                .find(responseBody)
                ?.groupValues
                ?.get(1)
                ?.trim()
        return TransportState.entries.find { it.name == value } ?: TransportState.UNKNOWN
    }

    /**
     * Extract current track URI from GetPositionInfo response.
     */
    fun parseCurrentTrackUri(responseBody: String): String? =
        Regex("""<(?:[A-Za-z_][\w.-]*:)?TrackURI>\s*([^<]*)\s*</""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.replace("&amp;", "&")
            ?.takeIf { it.isNotEmpty() }

    fun parseCurrentTrackNumber(responseBody: String): Int? =
        Regex("""<(?:[A-Za-z_][\w.-]*:)?Track>\s*([^<]+)\s*</""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    fun parseNumberOfTracks(responseBody: String): Int? = parseIntElement(responseBody, "NrTracks")

    fun parseCurrentUri(responseBody: String): String? = parseTextElement(responseBody, "CurrentURI")

    fun parseNextUri(responseBody: String): String? = parseTextElement(responseBody, "NextURI")

    private fun parseIntElement(
        responseBody: String,
        name: String,
    ): Int? = parseTextElement(responseBody, name)?.toIntOrNull()

    private fun parseTextElement(
        responseBody: String,
        name: String,
    ): String? =
        Regex("""<(?:[A-Za-z_][\w.-]*:)?$name>\s*([^<]*)\s*</""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.replace("&amp;", "&")
            ?.takeIf { it.isNotEmpty() }

    fun parseRelativeTimeSeconds(responseBody: String): Double? {
        val value = Regex("""<(?:[A-Za-z_][\w.-]*:)?RelTime>\s*([^<]+)\s*</""").find(responseBody)?.groupValues?.get(1) ?: return null
        val parts = value.trim().split(":")
        if (parts.size != 3) return null
        val hours = parts[0].toDoubleOrNull() ?: return null
        val minutes = parts[1].toDoubleOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return hours * 3600 + minutes * 60 + seconds
    }

    fun parseTrackDurationSeconds(responseBody: String): Double? {
        val value =
            Regex("""<(?:[A-Za-z_][\w.-]*:)?TrackDuration>\s*([^<]+)\s*</""")
                .find(responseBody)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: return null
        val parts = value.split(":")
        if (parts.size != 3) return null
        val hours = parts[0].toDoubleOrNull() ?: return null
        val minutes = parts[1].toDoubleOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return hours * 3600 + minutes * 60 + seconds
    }
}
