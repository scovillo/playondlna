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

package io.github.scovillo.playondlna.dlna.control.soap

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.control.DlnaTransport
import io.github.scovillo.playondlna.dlna.control.PlaybackCommand
import io.github.scovillo.playondlna.dlna.control.PlaybackMediaInfo
import io.github.scovillo.playondlna.dlna.control.PlaybackPositionInfo
import io.github.scovillo.playondlna.dlna.control.TransportState
import io.github.scovillo.playondlna.server.MediaFileResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SoapDlnaTransport(
    private val soapClient: SoapClient = sharedSoapClient,
    private val extractor: SoapResponseExtractor = sharedExtractor,
) : DlnaTransport {
    override suspend fun playFile(
        device: DlnaDevice,
        resource: MediaFileResource,
    ) = play(device, resource.url, resource.metaDataDidlLite)

    internal suspend fun play(
        device: DlnaDevice,
        url: String,
        metadata: String,
    ) {
        val serviceUrl = device.requireAvTransportUrl()
        execute(SetAVTransportUriCommand(serviceUrl, url, metadata))
        execute(PlayCommand(serviceUrl))
    }

    override suspend fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        val serviceUrl = device.requireAvTransportUrl()
        when (command) {
            PlaybackCommand.PLAY -> execute(PlayCommand(serviceUrl))
            PlaybackCommand.PAUSE -> execute(PauseCommand(serviceUrl))
            PlaybackCommand.STOP -> execute(StopCommand(serviceUrl))
            PlaybackCommand.NEXT -> execute(NextCommand(serviceUrl))
            PlaybackCommand.PREVIOUS -> execute(PreviousCommand(serviceUrl))
        }
    }

    override suspend fun transportState(device: DlnaDevice): TransportState {
        val state = extractor.parseTransportState(execute(GetTransportInfoCommand(device.requireAvTransportUrl())))
        AppLog.i("SoapPlayback", "GetTransportInfo response: state=$state")
        return state
    }

    override suspend fun currentTrackUri(device: DlnaDevice): String = extractor.parseCurrentTrackUri(execute(GetPositionInfoCommand(device.requireAvTransportUrl())))

    override suspend fun currentPositionSeconds(device: DlnaDevice): Double = extractor.parseRelativeTimeSeconds(execute(GetPositionInfoCommand(device.requireAvTransportUrl())))

    override suspend fun positionInfo(device: DlnaDevice): PlaybackPositionInfo {
        val response = execute(GetPositionInfoCommand(device.requireAvTransportUrl()))
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

    override suspend fun mediaInfo(device: DlnaDevice): PlaybackMediaInfo {
        val response = execute(GetMediaInfoCommand(device.requireAvTransportUrl()))
        val info =
            PlaybackMediaInfo(
                numberOfTracks = extractor.parseNumberOfTracks(response),
                currentUri = extractor.parseCurrentUri(response),
                nextUri = extractor.parseNextUri(response),
            )
        AppLog.i("SoapPlayback", "GetMediaInfo response: tracks=${info.numberOfTracks}, currentUri=${info.currentUri}, nextUri=${info.nextUri}")
        return info
    }

    override suspend fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) {
        val serviceUrl = device.requireAvTransportUrl()
        val value = seconds.toInt()
        execute(SeekCommand(serviceUrl, "%02d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60)))
    }

    private suspend fun execute(command: SoapCommand): String = withContext(Dispatchers.IO) { soapClient.execute(command) }

    private companion object {
        val sharedSoapClient = OkHttpSoapClient()
        val sharedExtractor = SoapResponseExtractor()
    }
}
