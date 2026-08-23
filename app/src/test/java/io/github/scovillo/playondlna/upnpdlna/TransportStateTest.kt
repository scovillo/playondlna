package io.github.scovillo.playondlna.upnpdlna

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportStateTest {
    @Test
    fun parsesNamespacedTransportState() {
        val response =
            """<s:Envelope><s:Body><u:GetTransportInfoResponse>""" +
                """<CurrentTransportState>PLAYING</CurrentTransportState>""" +
                """</u:GetTransportInfoResponse></s:Body></s:Envelope>"""

        assertEquals(TransportState.PLAYING, parseTransportState(response))
    }

    @Test
    fun parsesPrefixedTransportState() {
        assertEquals(
            TransportState.PAUSED_PLAYBACK,
            parseTransportState("<u:CurrentTransportState>PAUSED_PLAYBACK</u:CurrentTransportState>"),
        )
    }

    @Test
    fun parsesCurrentTrackUriAndXmlAmpersand() {
        assertEquals(
            "http://phone/video.mp4?a=1&b=2",
            parseCurrentTrackUri("<TrackURI>http://phone/video.mp4?a=1&amp;b=2</TrackURI>"),
        )
    }

    @Test
    fun recognizesIllegalMimeTypeAsPlaylistCompatibilityError() {
        val body = "<errorCode>714</errorCode><errorDescription>Illegal MIME-type</errorDescription>"

        assertEquals(true, isUnsupportedPlaylistError(UpnpActionException("SetAVTransportURI", 500, body)))
        assertEquals(false, isUnsupportedPlaylistError(UpnpActionException("SetAVTransportURI", 500, "")))
        assertEquals(false, isUnsupportedPlaylistError(java.io.IOException("network unavailable")))
    }

    @Test
    fun recognizesUnsupportedAction() {
        val exception = UpnpActionException("Next", 500, "<errorCode>401</errorCode>")

        assertEquals(true, isUnsupportedActionError(exception))
    }

    @Test
    fun recognizesUnsupportedTrackSeek() {
        val exception = UpnpActionException("Seek", 500, "<errorCode>501</errorCode>")

        assertEquals(true, isUnsupportedTrackSeekError(exception))
    }

    @Test
    fun returnsUnknownForMissingTransportState() {
        assertEquals(TransportState.UNKNOWN, parseTransportState("<s:Envelope/>"))
    }
}
