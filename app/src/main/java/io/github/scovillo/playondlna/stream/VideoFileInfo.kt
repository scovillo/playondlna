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

package io.github.scovillo.playondlna.stream

import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.Locale

class VideoFileInfo(private val extractor: StreamExtractor) {
    val title: String
        get() {
            return this.extractor.name
        }

    val id: String
        get() {
            return this.extractor.id
        }

    val duration: String
        get() {
            val hours = this.extractor.length / 3600
            val minutes = (this.extractor.length % 3600) / 60
            val secs = this.extractor.length % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        }

    val uploader: String
        get() {
            this.extractor.length
            return this.extractor.uploaderName
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
                            <res protocolInfo="http-get:*:video/mp4:*" duration="${this.duration}">
                                $url
                            </res>
                          </item>
                        </DIDL-Lite>
                    """.trimIndent()
        }
}