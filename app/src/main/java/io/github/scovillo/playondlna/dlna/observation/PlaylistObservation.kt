package io.github.scovillo.playondlna.dlna.observation

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.transport.PlaybackCommand
import io.github.scovillo.playondlna.dlna.transport.TransportState
import io.github.scovillo.playondlna.dlna.transport.soap.SoapDlnaTransport
import io.github.scovillo.playondlna.server.LocalIpAddress
import io.github.scovillo.playondlna.server.MediaFileResource
import io.github.scovillo.playondlna.server.ServerPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter

class PlaylistObservation(private val updates: SharedFlow<PlaybackSession>, private val localIpAddress: LocalIpAddress, private val serverPort: ServerPort) {
    private val client = SoapDlnaTransport()

    private val marks = mutableMapOf<String, Int>()

    suspend fun observe() {
        updates.filter { it.playlist.size > 1 }.collect {
            if (it.currentState == TransportState.PLAYING) {
                val isFilePlaybackEnd = it.hasNext && it.positionInfo.durationSeconds > 0 && it.positionInfo.positionSeconds >= it.positionInfo.durationSeconds - 2
                AppLog.d("PlaylistObservation", "Session of ${it.device.friendlyName} isFilePlaybackEnd=$isFilePlaybackEnd")
                if (isFilePlaybackEnd) {
                    marks[it.id] = it.predictIndex(PlaybackCommand.NEXT)
                }
            } else if (it.currentState == TransportState.STOPPED && it.id in marks) {
                AppLog.i("PlaylistObservation", "Playlist end reached for ${it.device.friendlyName}, playing next...")
                val index = marks.remove(it.id) ?: return@collect
                val item = it.playlist.items.getOrNull(index) ?: return@collect
                try {
                    client.playFile(it.device, MediaFileResource(localIpAddress, serverPort, item))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLog.w("PlaylistObservation", "Could not advance playlist for ${it.device.friendlyName}: ${error.message}")
                }
            }
        }
    }
}
