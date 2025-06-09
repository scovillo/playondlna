package com.example.upnpdlna

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
            return this.fileName.split(videoFileNameSeparator)[2]
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