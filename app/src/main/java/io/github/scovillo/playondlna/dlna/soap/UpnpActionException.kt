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

class UpnpActionException(
    val action: String,
    httpCode: Int,
    val responseBody: String,
) : Exception("$action failed: HTTP $httpCode, UPnP ${extractUpnpErrorCode(responseBody) ?: "unknown"}") {
    val upnpErrorCode: Int? = extractUpnpErrorCode(responseBody)

    companion object {
        private val errorCodeRegex = Regex("""<(?:[A-Za-z_][\w.-]*:)?errorCode>\s*(\d+)\s*</""")

        fun isUnsupportedPlaylist(exception: Throwable): Boolean =
            exception is UpnpActionException &&
                exception.action == "SetAVTransportURI" &&
                (exception.upnpErrorCode == 714 || exception.responseBody.contains("Illegal MIME-type", ignoreCase = true))

        fun isUnsupportedAction(exception: Throwable): Boolean = exception is UpnpActionException && exception.upnpErrorCode == 401

        fun isTransitionInProgress(exception: Throwable): Boolean = exception is UpnpActionException && exception.action == "Play" && exception.upnpErrorCode == 701

        private fun extractUpnpErrorCode(responseBody: String): Int? =
            errorCodeRegex
                .find(responseBody)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
    }
}
