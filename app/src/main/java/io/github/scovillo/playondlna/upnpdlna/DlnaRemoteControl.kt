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

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.server.VideoFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

/** Transport boundary used by [DlnaRemoteControl]. */
interface DlnaTransport {
    fun play(
        device: DlnaDevice,
        media: DlnaMedia,
    )

    fun command(
        device: DlnaDevice,
        command: TransportCommand,
    )

    fun transportState(device: DlnaDevice): TransportState

    fun currentTrackUri(device: DlnaDevice): String?

    fun seekToTrack(
        device: DlnaDevice,
        trackNumber: Int,
    )
}

object SoapDlnaTransport : DlnaTransport {
    override fun play(
        device: DlnaDevice,
        media: DlnaMedia,
    ) {
        playUriOnDevice(device.requireAvTransportUrl(), media)
    }

    override fun command(
        device: DlnaDevice,
        command: TransportCommand,
    ) {
        sendTransportCommand(device.requireAvTransportUrl(), command)
    }

    override fun transportState(device: DlnaDevice): TransportState = transportState(device.requireAvTransportUrl())

    override fun currentTrackUri(device: DlnaDevice): String? = currentTrackUri(device.requireAvTransportUrl())

    override fun seekToTrack(
        device: DlnaDevice,
        trackNumber: Int,
    ) {
        seekToTrack(device.requireAvTransportUrl(), trackNumber)
    }
}

/**
 * Controls a single DLNA playback session without any dependency on Android UI or persistence.
 *
 * The host supplies persistence and user-feedback callbacks, while the transport can be replaced
 * in tests or by another UPnP implementation.
 */
class DlnaRemoteControl(
    private val scope: CoroutineScope,
    private val transport: DlnaTransport = SoapDlnaTransport,
    private val nativePlaylistSupport: suspend (String) -> Boolean? = { null },
    private val saveNativePlaylistSupport: suspend (String, Boolean) -> Unit = { _, _ -> },
    private val onIncompatibleDevice: suspend (DlnaDevice) -> Unit = {},
    private val onPlaybackFailure: suspend (Throwable) -> Unit = {},
) {
    private var playbackJob: Job? = null
    private var nativeSyncJob: Job? = null
    private val playbackSession = MutableStateFlow<PlaybackSession?>(null)

    fun playVideo(
        device: DlnaDevice,
        videoFile: VideoFile,
    ) = playMedia(device, DlnaMedia(videoFile.url, videoFile.metaData))

    fun playMedia(
        device: DlnaDevice,
        media: DlnaMedia,
    ) {
        cancelPlayback()
        scope.launch(Dispatchers.IO) {
            if (device.avTransportUrl == null) {
                onIncompatibleDevice(device)
                return@launch
            }
            runCatching { transport.play(device, media) }.onFailure { onPlaybackFailure(it) }
        }
    }

    fun playPlaylist(
        device: DlnaDevice,
        nativePlaylist: DlnaMedia,
        videoFiles: List<VideoFile>,
    ) {
        cancelPlayback()
        playbackJob =
            scope.launch(Dispatchers.IO) {
                if (device.avTransportUrl == null) {
                    onIncompatibleDevice(device)
                    return@launch
                }
                try {
                    val requiresNativePlaylist = device.isKodiRenderer()
                    if (requiresNativePlaylist || nativePlaylistSupport(device.usn) != false) {
                        try {
                            transport.play(device, nativePlaylist)
                            saveNativePlaylistSupport(device.usn, true)
                            playbackSession.value = PlaybackSession(device, PlaybackMode.NATIVE_PLAYLIST, videoFiles, 0, nativePlaylist)
                            startNativePlaylistSync(device)
                            return@launch
                        } catch (exception: Exception) {
                            if (requiresNativePlaylist || !isUnsupportedPlaylistError(exception)) throw exception
                            saveNativePlaylistSupport(device.usn, false)
                        }
                    }
                    playbackSession.value = PlaybackSession(device, PlaybackMode.APP_MANAGED, videoFiles, 0, nativePlaylist)
                    playAppPlaylistFrom(0)
                } catch (_: CancellationException) {
                    // A newer command superseded this playback session.
                } catch (exception: Exception) {
                    onPlaybackFailure(exception)
                }
            }
    }

    fun command(
        device: DlnaDevice,
        command: TransportCommand,
    ) {
        if (playbackSession.value?.device?.location != device.location) {
            sendCommand(device, command)
            return
        }
        val previousPlaybackJob = playbackJob
        AppLog.i("AppManagedPlaylist", "Manual $command requested at index ${playbackSession.value?.currentIndex}")
        previousPlaybackJob?.cancel()
        playbackJob =
            scope.launch(Dispatchers.IO) {
                try {
                    previousPlaybackJob?.join()
                    val currentSession = playbackSession.value?.takeIf { it.device.location == device.location } ?: return@launch
                    AppLog.i("AppManagedPlaylist", "Manual $command runs at index ${currentSession.currentIndex}")
                    when {
                        command == TransportCommand.NEXT || command == TransportCommand.PREVIOUS -> changeTrack(currentSession, command)
                        command == TransportCommand.PLAY && currentSession.transportState == TransportState.STOPPED ->
                            resumeStoppedSession(currentSession)
                        command == TransportCommand.PLAY -> resumeSession(currentSession)
                        command == TransportCommand.PAUSE || command == TransportCommand.STOP -> pauseOrStop(currentSession, command)
                    }
                } catch (_: CancellationException) {
                    AppLog.i("AppManagedPlaylist", "Manual $command cancelled")
                    // A newer command superseded this one.
                } catch (exception: Exception) {
                    AppLog.e("AppManagedPlaylist", "Manual $command failed", exception)
                    if (isUnsupportedActionError(exception)) markUnsupported(command)
                    onPlaybackFailure(exception)
                }
            }
    }

    private fun sendCommand(
        device: DlnaDevice,
        command: TransportCommand,
    ) {
        scope.launch(Dispatchers.IO) {
            if (device.avTransportUrl == null) {
                onIncompatibleDevice(device)
                return@launch
            }
            runCatching { transport.command(device, command) }.onFailure { onPlaybackFailure(it) }
        }
    }

    private suspend fun changeTrack(
        session: PlaybackSession,
        command: TransportCommand,
    ) {
        val index = playlistIndexForCommand(session.currentIndex, session.videoFiles.lastIndex, command) ?: return
        when {
            session.transportState == TransportState.STOPPED -> playbackSession.value = session.copy(currentIndex = index)
            session.mode == PlaybackMode.APP_MANAGED -> playAppPlaylistFrom(index)
            else -> {
                transport.command(session.device, command)
                playbackSession.value = session.copy(currentIndex = index)
            }
        }
    }

    private suspend fun resumeStoppedSession(session: PlaybackSession) {
        if (session.mode == PlaybackMode.NATIVE_PLAYLIST) {
            try {
                transport.seekToTrack(session.device, session.currentIndex + 1)
                transport.command(session.device, TransportCommand.PLAY)
            } catch (exception: Exception) {
                if (!isUnsupportedTrackSeekError(exception)) throw exception
                transport.play(session.device, session.nativePlaylist.startingAt(session.currentIndex))
            }
            playbackSession.value = session.copy(transportState = TransportState.PLAYING)
        } else {
            playbackSession.value = session.copy(transportState = TransportState.PLAYING)
            playAppPlaylistFrom(session.currentIndex)
        }
    }

    private suspend fun resumeSession(session: PlaybackSession) {
        transport.command(session.device, TransportCommand.PLAY)
        playbackSession.value = session.copy(transportState = TransportState.PLAYING)
        if (session.mode == PlaybackMode.APP_MANAGED) continueAppPlaylist(session)
    }

    private fun pauseOrStop(
        session: PlaybackSession,
        command: TransportCommand,
    ) {
        transport.command(session.device, command)
        playbackSession.value =
            session.copy(transportState = if (command == TransportCommand.STOP) TransportState.STOPPED else TransportState.PAUSED_PLAYBACK)
    }

    private fun markUnsupported(command: TransportCommand) {
        playbackSession.value = playbackSession.value?.let { it.copy(unsupportedCommands = it.unsupportedCommands + command) }
    }

    private suspend fun playAppPlaylistFrom(startIndex: Int) {
        var session = playbackSession.value ?: return
        AppLog.i("AppManagedPlaylist", "Continue from index $startIndex of ${session.videoFiles.size}")
        for (index in startIndex..session.videoFiles.lastIndex) {
            currentCoroutineContext().ensureActive()
            session = session.copy(currentIndex = index)
            playbackSession.value = session
            val videoFile = session.videoFiles[index]
            AppLog.i("AppManagedPlaylist", "Start ${index + 1}/${session.videoFiles.size}: ${videoFile.id}")
            try {
                transport.play(session.device, DlnaMedia(videoFile.url, videoFile.metaData))
            } catch (exception: Exception) {
                if (!isTransitionInProgressError(exception)) throw exception
                AppLog.i("AppManagedPlaylist", "Renderer is already transitioning to ${index + 1}/${session.videoFiles.size}")
            }
            currentCoroutineContext().ensureActive()
            if (index < session.videoFiles.lastIndex) awaitPlaybackEnd(session.device, videoFile.url)
        }
    }

    private suspend fun continueAppPlaylist(session: PlaybackSession) {
        if (session.currentIndex >= session.videoFiles.lastIndex) return
        awaitPlaybackEnd(session.device, session.videoFiles[session.currentIndex].url)
        playAppPlaylistFrom(session.currentIndex + 1)
    }

    private suspend fun awaitPlaybackEnd(
        device: DlnaDevice,
        expectedTrackUri: String,
    ) {
        var observation = PlaybackEndObservation()
        var startupPolls = 0
        var previousState: TransportState? = null
        while (true) {
            delay(1000.milliseconds)
            val state = transportStateWithRetry(device)
            currentCoroutineContext().ensureActive()
            val trackUri = runCatching { transport.currentTrackUri(device) }.getOrNull()
            currentCoroutineContext().ensureActive()
            if (state != previousState) {
                AppLog.i("AppManagedPlaylist", "Track state: $state, URI: $trackUri")
                previousState = state
            }
            val result = observation.observe(state, trackUri, expectedTrackUri)
            observation = result.observation
            if (result.playbackEnded) {
                AppLog.i("AppManagedPlaylist", "Track ended: $expectedTrackUri")
                return
            } else {
                AppLog.i("AppManagedPlaylist", "playbackEnded: ${false}")
            }
            if (!observation.playbackObserved && ++startupPolls >= 15) error("Renderer did not enter an active playback state")
        }
    }

    private suspend fun transportStateWithRetry(device: DlnaDevice): TransportState {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return transport.transportState(device)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                lastError = exception
                if (attempt < 2) delay(500.milliseconds)
            }
        }
        throw lastError ?: IllegalStateException("GetTransportInfo failed")
    }

    private fun startNativePlaylistSync(device: DlnaDevice) {
        nativeSyncJob?.cancel()
        nativeSyncJob =
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(2000.milliseconds)
                    val trackUri = runCatching { transport.currentTrackUri(device) }.getOrNull() ?: continue
                    val session = playbackSession.value?.takeIf { it.mode == PlaybackMode.NATIVE_PLAYLIST } ?: return@launch
                    val index = session.videoFiles.indexOfFirst { it.url == trackUri }
                    if (index >= 0 && index != session.currentIndex) playbackSession.value = session.copy(currentIndex = index)
                }
            }
    }

    private fun cancelPlayback() {
        playbackJob?.cancel()
        nativeSyncJob?.cancel()
        playbackSession.value = null
    }
}

private fun DlnaDevice.requireAvTransportUrl(): String = requireNotNull(avTransportUrl) { "No AVTransport URL for $friendlyName" }

internal fun DlnaDevice.isKodiRenderer(): Boolean = sequenceOf(friendlyName, manufacturer, modelName).any { it.contains("kodi", ignoreCase = true) }

enum class PlaybackMode { NATIVE_PLAYLIST, APP_MANAGED }

data class PlaybackSession(
    val device: DlnaDevice,
    val mode: PlaybackMode,
    val videoFiles: List<VideoFile>,
    val currentIndex: Int,
    val nativePlaylist: DlnaMedia,
    val unsupportedCommands: Set<TransportCommand> = emptySet(),
    val transportState: TransportState = TransportState.PLAYING,
)

fun playlistIndexForCommand(
    currentIndex: Int,
    lastIndex: Int,
    command: TransportCommand,
): Int? =
    when (command) {
        TransportCommand.NEXT -> (currentIndex + 1).takeIf { it <= lastIndex }
        TransportCommand.PREVIOUS -> (currentIndex - 1).takeIf { it >= 0 }
        else -> null
    }

internal data class PlaybackEndObservation(
    val expectedTrackObserved: Boolean = false,
    val consecutiveActivePolls: Int = 0,
    val playbackObserved: Boolean = false,
) {
    fun observe(
        transportState: TransportState,
        currentTrackUri: String?,
        expectedTrackUri: String,
    ): PlaybackEndResult {
        val expectedTrackObserved = expectedTrackObserved || trackUrisMatch(currentTrackUri, expectedTrackUri)
        val isActive =
            transportState in
                setOf(TransportState.PLAYING, TransportState.TRANSITIONING, TransportState.PAUSED_PLAYBACK)
        val consecutiveActivePolls = if (isActive) consecutiveActivePolls + 1 else 0
        // Require the expected URI to have been seen in an earlier poll. Transport
        // state and position are separate SOAP calls and can straddle a transition.
        // After three stable active polls, fall back to transport state because some
        // renderers omit or rewrite TrackURI even though playback works.
        val stateBelongsToExpectedTrack = this.expectedTrackObserved || consecutiveActivePolls >= 3
        val playbackObserved =
            playbackObserved ||
                (stateBelongsToExpectedTrack && isActive)
        val playbackEnded =
            playbackObserved &&
                transportState in setOf(TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT)
        return PlaybackEndResult(
            observation = PlaybackEndObservation(expectedTrackObserved, consecutiveActivePolls, playbackObserved),
            playbackEnded = playbackEnded,
        )
    }
}

internal fun trackUrisMatch(
    currentTrackUri: String?,
    expectedTrackUri: String,
): Boolean {
    if (currentTrackUri == null) return false
    if (currentTrackUri == expectedTrackUri) return true
    return runCatching {
        val currentPath = URI(currentTrackUri).normalize().path
        val expectedPath = URI(expectedTrackUri).normalize().path
        currentPath.isNotEmpty() && currentPath == expectedPath
    }.getOrDefault(false)
}

internal data class PlaybackEndResult(
    val observation: PlaybackEndObservation,
    val playbackEnded: Boolean,
)
