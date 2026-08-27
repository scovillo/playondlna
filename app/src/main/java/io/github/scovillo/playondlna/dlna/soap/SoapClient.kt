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

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Abstraction for SOAP communication with UPnP devices.
 * Can be replaced with mock implementations for testing.
 */
interface SoapClient {
    /**
     * Execute a SOAP command and return the response body.
     * @throws UpnpActionException if the SOAP request fails
     */
    fun execute(command: SoapCommand): String
}

/**
 * OkHttp-based SOAP client for real UPnP communication.
 */
class OkHttpSoapClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) : SoapClient {
    override fun execute(command: SoapCommand): String {
        val payload = command.createPayload()
        Log.i("SoapClient", "${command.actionName}: $payload")

        val request =
            Request.Builder()
                .url(command.serviceUrl)
                .post(payload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .header("SOAPAction", "\"${command.soapAction}\"")
                .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e("SoapClient =>", payload)
                Log.e("SoapClient <=", body)
                throw UpnpActionException(command.actionName, response.code, body)
            }
            body
        }
    }
}
