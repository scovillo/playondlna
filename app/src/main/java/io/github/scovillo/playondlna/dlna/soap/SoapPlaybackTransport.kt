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

import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackTransport
import io.github.scovillo.playondlna.dlna.control.TransportState
import okhttp3.OkHttpClient

class SoapPlaybackTransport(
    private val serviceUrl: String,
    private val soapClient: SoapClient,
    private val extractor: SoapResponseExtractor = SoapResponseExtractor(),
) : PlaybackTransport {
    override fun play(
        url: String,
        metadata: String,
    ) {
        soapClient.execute(SetAVTransportUriCommand(serviceUrl, url, metadata))
        soapClient.execute(PlayCommand(serviceUrl))
    }

    override fun command(command: PlaybackCommand) {
        when (command) {
            PlaybackCommand.PLAY -> soapClient.execute(PlayCommand(serviceUrl))
            PlaybackCommand.PAUSE -> soapClient.execute(PauseCommand(serviceUrl))
            PlaybackCommand.STOP -> soapClient.execute(StopCommand(serviceUrl))
            PlaybackCommand.NEXT -> soapClient.execute(NextCommand(serviceUrl))
            PlaybackCommand.PREVIOUS -> soapClient.execute(PreviousCommand(serviceUrl))
        }
    }

    override fun transportState(): TransportState = extractor.parseTransportState(soapClient.execute(GetTransportInfoCommand(serviceUrl)))

    override fun currentTrackUri(): String? = extractor.parseCurrentTrackUri(soapClient.execute(GetPositionInfoCommand(serviceUrl)))
}

class SoapPlaybackTransportFactory(
    private val soapClientFactory: () -> SoapClient = { OkHttpSoapClient(OkHttpClient()) },
    private val extractorFactory: () -> SoapResponseExtractor = { SoapResponseExtractor() },
) {
    fun create(serviceUrl: String): PlaybackTransport =
        SoapPlaybackTransport(
            serviceUrl = serviceUrl,
            soapClient = soapClientFactory(),
            extractor = extractorFactory(),
        )
}
