package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.DlnaDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    internal val isStopRequested: Boolean
        get() = stopOnPlayerStop

    /** Starts a fresh polling run. Calling it again restarts this player's existing observation. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun start() {
        AppLog.i("PlaybackObservation", "Starting playback observation for ${device.friendlyName}")
        val previousJob = job
        previousJob?.cancel()
        stopOnPlayerStop = false
        val playbackJob = scope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) { previousJob?.join() }
            while (isActive) {
                try {
                    val transportState = transport.transportState(device)
                    currentCoroutineContext().ensureActive()
                    val positionInfo = transport.positionInfo(device)
                    currentCoroutineContext().ensureActive()
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
                    currentCoroutineContext().ensureActive()
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

    /**
     * Requests cancellation of polling and returns immediately without waiting for completion.
     * An in-flight blocking HTTP request may continue until it completes or times out.
     * Use [cancelAndJoin] before replacing this observation when polling must not overlap.
     * This does not send a Stop command to the renderer.
     */
    @Synchronized
    fun cancel() {
        job?.cancel()
    }

    /**
     * Requests cancellation and suspends until the current polling job has finished.
     * Unlike [cancel], this waits for any in-flight blocking HTTP request to complete or time out,
     * without blocking the calling thread. Use it before starting a replacement observation.
     * Waiting is cancellable; cancellation of the caller can end the wait before polling finishes.
     * This does not send a Stop command to the renderer.
     */
    suspend fun cancelAndJoin() {
        val previousJob = synchronized(this) { job.also { it?.cancel() } }
        previousJob?.join()
    }

    /** Keeps polling through transient states until a requested stop is confirmed. */
    @Synchronized
    fun stopOnPlayerStop() {
        stopOnPlayerStop = true
    }
}
