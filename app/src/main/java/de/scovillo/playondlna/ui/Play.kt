package de.scovillo.playondlna.ui

import de.scovillo.playondlna.VideoFile
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
        throw Exception("SetAVTransportURI failed: ${setUriResponse.code}")
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
        throw Exception("Play command failed: ${playResponse.code}")
    }
    playResponse.close()
}