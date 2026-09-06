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

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackMediaInfo
import io.github.scovillo.playondlna.dlna.control.PlaybackPositionInfo
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

    override fun transportState(): TransportState {
        val state = extractor.parseTransportState(soapClient.execute(GetTransportInfoCommand(serviceUrl)))
        AppLog.i("SoapPlayback", "GetTransportInfo response: state=$state")
        return state
    }

    override fun currentTrackUri(): String? = extractor.parseCurrentTrackUri(soapClient.execute(GetPositionInfoCommand(serviceUrl)))

    override fun currentPositionSeconds(): Double? = extractor.parseRelativeTimeSeconds(soapClient.execute(GetPositionInfoCommand(serviceUrl)))

    override fun positionInfo(): PlaybackPositionInfo {
        val response = soapClient.execute(GetPositionInfoCommand(serviceUrl))
        return PlaybackPositionInfo(
            trackUri = extractor.parseCurrentTrackUri(response),
            positionSeconds = extractor.parseRelativeTimeSeconds(response),
            trackNumber = extractor.parseCurrentTrackNumber(response),
            durationSeconds = extractor.parseTrackDurationSeconds(response),
        ).also {
            AppLog.i(
                "SoapPlayback",
                "GetPositionInfo response: track=${it.trackNumber}, uri=${it.trackUri}, position=${it.positionSeconds}, duration=${it.durationSeconds}",
            )
        }
    }

    override fun mediaInfo(): PlaybackMediaInfo {
        val response = soapClient.execute(GetMediaInfoCommand(serviceUrl))
        val info =
            PlaybackMediaInfo(
                numberOfTracks = extractor.parseNumberOfTracks(response),
                currentUri = extractor.parseCurrentUri(response),
                nextUri = extractor.parseNextUri(response),
            )
        AppLog.i("SoapPlayback", "GetMediaInfo response: tracks=${info.numberOfTracks}, currentUri=${info.currentUri}, nextUri=${info.nextUri}")
        return info
    }

    override fun seekTo(seconds: Double) {
        val value = seconds.toInt()
        soapClient.execute(SeekCommand(serviceUrl, "%02d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60)))
    }
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
