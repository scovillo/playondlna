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
}
