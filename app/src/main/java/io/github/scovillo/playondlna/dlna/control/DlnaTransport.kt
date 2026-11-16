package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.server.MediaFileResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/** Playback operations for a DLNA renderer. */
interface DlnaTransport {
    suspend fun playFile(
        device: DlnaDevice,
        resource: MediaFileResource,
    )

    suspend fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    )

    suspend fun transportState(device: DlnaDevice): TransportState

    suspend fun currentTrackUri(device: DlnaDevice): String

    suspend fun currentPositionSeconds(device: DlnaDevice): Double

    suspend fun positionInfo(device: DlnaDevice): PlaybackPositionInfo = PlaybackPositionInfo(currentTrackUri(device), currentPositionSeconds(device))

    /** Reads both responses concurrently. They are not an atomic renderer snapshot. */
    suspend fun playbackInfo(device: DlnaDevice): PlaybackInfo =
        withContext(Dispatchers.IO) {
            val state = async { transportState(device) }
            val position = async { positionInfo(device) }
            PlaybackInfo(state.await(), position.await())
        }

    suspend fun mediaInfo(device: DlnaDevice): PlaybackMediaInfo? = null

    suspend fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    )
}

data class PlaybackPositionInfo(
    val trackUri: String = "",
    val positionSeconds: Double = 0.0,
    val trackNumber: Int = 0,
    val durationSeconds: Double = 0.0,
)

data class PlaybackMediaInfo(
    val numberOfTracks: Int?,
    val currentUri: String?,
    val nextUri: String?,
)

/** Combined result of GetTransportInfo and GetPositionInfo. */
data class PlaybackInfo(
    val state: TransportState,
    val positionInfo: PlaybackPositionInfo,
)
