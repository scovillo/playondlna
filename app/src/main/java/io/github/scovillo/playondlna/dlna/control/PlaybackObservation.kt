package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.DlnaDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PlaybackStatus(
    val transportState: TransportState,
    val trackUri: String?,
    val positionSeconds: Double?,
    val trackNumber: Int?,
    val durationSeconds: Double?,
)

/** A single, shareable playback observation for one renderer. */
class PlaybackObservation(
    private val scope: CoroutineScope,
    private val device: DlnaDevice,
    private val transport: DlnaTransport,
    val delay: Duration = 1.seconds,
) {
    private val _status = MutableSharedFlow<PlaybackStatus>(replay = 1, extraBufferCapacity = 1)
    val status: SharedFlow<PlaybackStatus> = _status.asSharedFlow()

    private var job: Job? = null

    @Volatile
    private var stopOnPlayerStop = false

    /** Starts a fresh polling run. Calling it again restarts this player's existing observation. */
    @Synchronized
    fun start() {
        AppLog.i("PlaybackObservation", "Starting playback observation for ${device.friendlyName}")
        job?.cancel()
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val transportState = transport.transportState(device)
                        val positionInfo = transport.positionInfo(device)
                        _status.emit(
                            PlaybackStatus(
                                transportState,
                                positionInfo.trackUri,
                                positionInfo.positionSeconds,
                                positionInfo.trackNumber,
                                positionInfo.durationSeconds,
                            ),
                        )
                        if (stopOnPlayerStop && transportState == TransportState.STOPPED) break
                    } catch (exception: Exception) {
                        if (exception is CancellationException) throw exception
                        AppLog.w("PlaybackObservation", "Could not query ${device.friendlyName}: ${exception.message}")
                    }
                    delay(delay)
                }
            }
        job = playbackJob
        playbackJob.invokeOnCompletion {
            synchronized(this) {
                if (job === playbackJob) job = null
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Keeps polling through transient states until a requested stop is confirmed. */
    @Synchronized
    fun stopOnPlayerStop() {
        stopOnPlayerStop = true
    }
}
