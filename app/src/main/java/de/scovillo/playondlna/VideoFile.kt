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

const val videoFileNameSeparator = "+|-"

class VideoFile(val fileName: String) {
    val title: String
        get() {
            return this.fileName.split(videoFileNameSeparator)[0]
        }

    val id: String
        get() {
            return this.fileName.split(videoFileNameSeparator)[1]
        }

    val uploader: String
        get() {
            return this.fileName.split(videoFileNameSeparator)[2].replace(".mp4", "")
        }

    val url: String
        get() {
            return "http://${getLocalIpAddress()}:$serverPort/${this.id}"
        }

    val metaData: String
        get() {
            return """
                        <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                                   xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                                   xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                          <item id="${this.id}" parentID="0" restricted="1">
                            <dc:title>${this.title}</dc:title>
                            <dc:creator>${this.uploader}</dc:creator>
                            <upnp:class>object.item.videoItem</upnp:class>
                            <res protocolInfo="http-get:*:video/mp4:*">$url</res>
                          </item>
                        </DIDL-Lite>
                    """.trimIndent()
        }
}