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

/**
 * Template for SOAP commands sent to UPnP devices.
 * Each command encapsulates the payload, action name, and parsing logic.
 */
interface SoapCommand {
    /** Service URL where this command should be sent */
    val serviceUrl: String

    /** SOAP action name (e.g., "SetAVTransportURI") */
    val actionName: String

    /** Full SOAP action header value */
    val soapAction: String

    /** Create the SOAP XML payload for this command */
    fun createPayload(): String
}

/**
 * Set AV Transport URI and metadata on a device.
 */
class SetAVTransportUriCommand(
    override val serviceUrl: String,
    val url: String,
    val metadata: String,
) : SoapCommand {
    override val actionName = "SetAVTransportURI"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <CurrentURI>$url</CurrentURI>
              <CurrentURIMetaData>
              $metadata
              </CurrentURIMetaData>
            </u:SetAVTransportURI>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Play the current URI (resume playback at speed 1).
 */
class PlayCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "Play"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Play"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <Speed>1</Speed>
            </u:Play>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Pause playback.
 */
class PauseCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "Pause"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Pause"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
            </u:Pause>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Stop playback and return to initial state.
 */
class StopCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "Stop"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Stop"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
            </u:Stop>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Skip to next track in a playlist.
 */
class NextCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "Next"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Next"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Next xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
            </u:Next>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Skip to previous track in a playlist.
 */
class PreviousCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "Previous"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Previous"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Previous xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
            </u:Previous>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Query transport state (playing, paused, stopped, etc.).
 */
class GetTransportInfoCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "GetTransportInfo"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:GetTransportInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
            </u:GetTransportInfo>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}

/**
 * Query current track URI and position information.
 */
class GetPositionInfoCommand(override val serviceUrl: String) : SoapCommand {
    override val actionName = "GetPositionInfo"
    override val soapAction = "urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo"

    override fun createPayload(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <u:GetPositionInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
            </u:GetPositionInfo>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}
