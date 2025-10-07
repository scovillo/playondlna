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
import io.github.scovillo.playondlna.stream.VideoFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

val client = OkHttpClient()

fun playUriOnDevice(avTransportUrl: String, videoFile: VideoFile) {
    val setUriSoap = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" 
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <CurrentURI>${videoFile.url}</CurrentURI>
              <CurrentURIMetaData>
              ${videoFile.metaData}
              </CurrentURIMetaData>
            </u:SetAVTransportURI>
          </s:Body>
        </s:Envelope>
    """.trimIndent()
    val setUriRequest = Request.Builder()
        .url(avTransportUrl)
        .post(setUriSoap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
        .header("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
        .build()
    val setUriResponse = client.newCall(setUriRequest).execute()
    if (!setUriResponse.isSuccessful) {
        Log.e("playUriOnDevice", setUriSoap)
        throw Exception("SetAVTransportURI failed: ${setUriResponse.code} - ${setUriResponse.message}")
    }
    setUriResponse.close()
    val playSoap = """
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
    val playRequest = Request.Builder()
        .url(avTransportUrl)
        .post(playSoap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
        .header("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"")
        .build()
    val playResponse = client.newCall(playRequest).execute()
    if (!playResponse.isSuccessful) {
        throw Exception("Play command failed: ${playResponse.code} - ${playResponse.message}")
    }
    playResponse.close()
}