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

package io.github.scovillo.playondlna.upnpdlna

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

val client = OkHttpClient()

fun playUriOnDevice(
    avTransportUrl: String,
    media: DlnaMedia,
) {
    val uriSoapPayload = avTransportUriPayload(media)
    Log.i("playUriOnDevice", uriSoapPayload)
    val setUriRequest =
        Request.Builder()
            .url(avTransportUrl)
            .post(uriSoapPayload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
            .build()
    val setUriResponse = client.newCall(setUriRequest).execute()
    if (!setUriResponse.isSuccessful) {
        val responseBody = setUriResponse.body?.string().orEmpty()
        Log.e("playUriOnDevice =>", uriSoapPayload)
        Log.e("playUriOnDevice <=", responseBody)
        throw UpnpActionException("SetAVTransportURI", setUriResponse.code, responseBody)
    }
    setUriResponse.close()
    val playSoapPayload =
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
    val playRequest =
        Request.Builder()
            .url(avTransportUrl)
            .post(playSoapPayload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"")
            .build()
    val playResponse = client.newCall(playRequest).execute()
    if (!playResponse.isSuccessful) {
        val responseBody = playResponse.body?.string().orEmpty()
        Log.e("playUriOnDevice =>", playSoapPayload)
        Log.e("playUriOnDevice <=", responseBody)
        throw UpnpActionException("Play", playResponse.code, responseBody)
    }
    playResponse.close()
}

fun avTransportUriPayload(media: DlnaMedia): String =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
      <s:Body>
        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
          <InstanceID>0</InstanceID>
          <CurrentURI>${media.url}</CurrentURI>
          <CurrentURIMetaData>
          ${media.metaData}
          </CurrentURIMetaData>
        </u:SetAVTransportURI>
      </s:Body>
    </s:Envelope>
    """.trimIndent()

fun transportState(avTransportUrl: String): TransportState {
    val payload =
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
    val request =
        Request.Builder()
            .url(avTransportUrl)
            .post(payload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"")
            .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw UpnpActionException("GetTransportInfo", response.code, response.body?.string().orEmpty())
        }
        return parseTransportState(response.body?.string().orEmpty())
    }
}

fun currentTrackUri(avTransportUrl: String): String? {
    val payload =
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
    val request =
        Request.Builder()
            .url(avTransportUrl)
            .post(payload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo\"")
            .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw UpnpActionException("GetPositionInfo", response.code, response.body?.string().orEmpty())
        }
        return parseCurrentTrackUri(response.body?.string().orEmpty())
    }
}

fun sendTransportCommand(
    avTransportUrl: String,
    command: TransportCommand,
) {
    val speed = if (command == TransportCommand.PLAY) "<Speed>1</Speed>" else ""
    val payload =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:${command.soapName} xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>$speed
            </u:${command.soapName}>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
    val request =
        Request.Builder()
            .url(avTransportUrl)
            .post(payload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#${command.soapName}\"")
            .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw UpnpActionException(command.soapName, response.code, response.body?.string().orEmpty())
        }
    }
}

fun seekToTrack(
    avTransportUrl: String,
    trackNumber: Int,
) {
    require(trackNumber > 0)
    val payload =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <Unit>TRACK_NR</Unit>
              <Target>$trackNumber</Target>
            </u:Seek>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
    val request =
        Request.Builder()
            .url(avTransportUrl)
            .post(payload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#Seek\"")
            .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw UpnpActionException("Seek", response.code, response.body?.string().orEmpty())
        }
    }
}

enum class TransportCommand(val soapName: String) {
    PLAY("Play"),
    PAUSE("Pause"),
    STOP("Stop"),
    NEXT("Next"),
    PREVIOUS("Previous"),
}

enum class TransportState {
    PLAYING,
    TRANSITIONING,
    PAUSED_PLAYBACK,
    STOPPED,
    NO_MEDIA_PRESENT,
    UNKNOWN,
}

fun parseTransportState(responseBody: String): TransportState {
    val value =
        Regex("""<(?:[A-Za-z_][\w.-]*:)?CurrentTransportState>\s*([^<]+)\s*</""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?.trim()
    return TransportState.entries.find { it.name == value } ?: TransportState.UNKNOWN
}

fun parseCurrentTrackUri(responseBody: String): String? =
    Regex("""<(?:[A-Za-z_][\w.-]*:)?TrackURI>\s*([^<]*)\s*</""")
        .find(responseBody)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.replace("&amp;", "&")
        ?.takeIf { it.isNotEmpty() }

class UpnpActionException(
    val action: String,
    httpCode: Int,
    val responseBody: String,
) : Exception("$action failed: HTTP $httpCode, UPnP ${parseUpnpErrorCode(responseBody) ?: "unknown"}") {
    val upnpErrorCode: Int? = parseUpnpErrorCode(responseBody)
}

fun parseUpnpErrorCode(responseBody: String): Int? =
    Regex("""<(?:[A-Za-z_][\w.-]*:)?errorCode>\s*(\d+)\s*</""")
        .find(responseBody)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

fun isUnsupportedPlaylistError(exception: Throwable): Boolean =
    exception is UpnpActionException &&
        exception.action == "SetAVTransportURI" &&
        (exception.upnpErrorCode == 714 || exception.responseBody.contains("Illegal MIME-type", ignoreCase = true))

fun isUnsupportedActionError(exception: Throwable): Boolean = exception is UpnpActionException && exception.upnpErrorCode == 401

fun isUnsupportedTrackSeekError(exception: Throwable): Boolean = exception is UpnpActionException && exception.action == "Seek" && exception.upnpErrorCode in setOf(401, 501)

/**
 * Some Samsung renderers start loading a new URI as part of SetAVTransportURI,
 * then reject the immediately following Play command while they are transitioning.
 */
fun isTransitionInProgressError(exception: Throwable): Boolean {
    return exception is UpnpActionException && exception.action == "Play" && exception.upnpErrorCode == 701
}
