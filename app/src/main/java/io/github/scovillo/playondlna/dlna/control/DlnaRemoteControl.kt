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

package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.server.VideoFile
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.DlnaMedia
import io.github.scovillo.playondlna.dlna.TransportState
import io.github.scovillo.playondlna.dlna.soap.SoapPlaybackTransportFactory
import io.github.scovillo.playondlna.dlna.soap.UpnpActionException
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
import kotlin.time.Duration.Companion.milliseconds

/** Transport boundary used by [DlnaRemoteControl] - provides legacy compatibility layer */
interface DlnaTransport {
    fun play(
        device: DlnaDevice,
        media: DlnaMedia,
    )

    fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    )

    fun transportState(device: DlnaDevice): TransportState

    fun currentTrackUri(device: DlnaDevice): String?

    fun seekToTrack(
        device: DlnaDevice,
        trackNumber: Int,
    )
}

/**
 * Adapter from DlnaTransport to PlaybackTransport.
 * Creates appropriate PlaybackTransport implementations for communication with a device.
 */
class SoapDlnaTransport : DlnaTransport {
    private val transportFactory = SoapPlaybackTransportFactory()

    private fun transportFor(device: DlnaDevice): PlaybackTransport = transportFactory.create(device.requireAvTransportUrl())

    override fun play(
        device: DlnaDevice,
        media: DlnaMedia,
    ) {
        transportFor(device).play(media)
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        transportFor(device).command(command)
    }

    override fun transportState(device: DlnaDevice): TransportState =
        transportFor(device).transportState()

    override fun currentTrackUri(device: DlnaDevice): String? = transportFor(device).currentTrackUri()

    override fun seekToTrack(
        device: DlnaDevice,
        trackNumber: Int,
    ) {
        transportFor(device).seekToTrack(trackNumber)
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
    private val transport: DlnaTransport = SoapDlnaTransport(),
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
    ) = playMedia(
        device,
        DlnaMedia(
            videoFile.url,
            videoFile.metaData,
        ),
    )

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
                    if (nativePlaylistSupport(device.usn) != false) {
                        try {
                            transport.play(device, nativePlaylist)
                            saveNativePlaylistSupport(device.usn, true)
                            playbackSession.value =
                                PlaybackSession(
                                    device,
                                    PlaybackMode.NATIVE_PLAYLIST,
                                    videoFiles,
                                    0,
                                    nativePlaylist,
                                )
                            startNativePlaylistSync(device)
                            return@launch
                        } catch (exception: Exception) {
                            if (
                                !UpnpActionException.isUnsupportedPlaylist(
                                    exception,
                                )
                            ) {
                                throw exception
                            }
                            saveNativePlaylistSupport(device.usn, false)
                        }
                    }
                    playbackSession.value =
                        PlaybackSession(
                            device,
                            PlaybackMode.APP_MANAGED,
                            videoFiles,
                            0,
                            nativePlaylist,
                        )
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
        command: PlaybackCommand,
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
                        command == PlaybackCommand.NEXT ||
                            command == PlaybackCommand.PREVIOUS -> {
                            changeTrack(currentSession, command)
                        }
                        command == PlaybackCommand.PLAY &&
                            currentSession.transportState == TransportState.STOPPED -> {
                            resumeStoppedSession(currentSession)
                        }
                        command == PlaybackCommand.PLAY -> {
                            resumeSession(currentSession)
                        }
                        command == PlaybackCommand.PAUSE ||
                            command == PlaybackCommand.STOP -> {
                            pauseOrStop(currentSession, command)
                        }
                    }
                } catch (_: CancellationException) {
                    AppLog.i("AppManagedPlaylist", "Manual $command cancelled")
                    // A newer command superseded this one.
                } catch (exception: Exception) {
                    AppLog.e("AppManagedPlaylist", "Manual $command failed", exception)
                    if (
                        UpnpActionException.isUnsupportedAction(
                            exception,
                        )
                    ) {
                        markUnsupported(command)
                    }
                    onPlaybackFailure(exception)
                }
            }
    }

    private fun sendCommand(
        device: DlnaDevice,
        command: PlaybackCommand,
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
        command: PlaybackCommand,
    ) {
        val index = playlistIndexForCommand(session.currentIndex, session.videoFiles.lastIndex, command) ?: return
        when {
            session.transportState == TransportState.STOPPED -> {
                playbackSession.value = session.copy(currentIndex = index)
            }
            session.mode == PlaybackMode.APP_MANAGED -> {
                playAppPlaylistFrom(index)
            }
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
                transport.command(session.device, PlaybackCommand.PLAY)
            } catch (exception: Exception) {
                if (
                    !UpnpActionException.isUnsupportedTrackSeek(
                        exception,
                    )
                ) {
                    throw exception
                }
                transport.play(session.device, session.nativePlaylist.startingAt(session.currentIndex))
            }
            playbackSession.value =
                session.copy(
                    transportState = TransportState.PLAYING,
                )
        } else {
            playbackSession.value =
                session.copy(
                    transportState = TransportState.PLAYING,
                )
            playAppPlaylistFrom(session.currentIndex)
        }
    }

    private suspend fun resumeSession(session: PlaybackSession) {
        transport.command(session.device, PlaybackCommand.PLAY)
        playbackSession.value =
            session.copy(
                transportState = TransportState.PLAYING,
            )
        if (session.mode == PlaybackMode.APP_MANAGED) continueAppPlaylist(session)
    }

    private fun pauseOrStop(
        session: PlaybackSession,
        command: PlaybackCommand,
    ) {
        transport.command(session.device, command)
        playbackSession.value =
            session.copy(
                transportState =
                    if (command == PlaybackCommand.STOP) {
                        TransportState.STOPPED
                    } else {
                        TransportState.PAUSED_PLAYBACK
                    },
            )
    }

    private fun markUnsupported(command: PlaybackCommand) {
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
                transport.play(
                    session.device,
                    DlnaMedia(
                        videoFile.url,
                        videoFile.metaData,
                    ),
                )
            } catch (exception: Exception) {
                if (
                    !UpnpActionException.isTransitionInProgress(
                        exception,
                    )
                ) {
                    throw exception
                }
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
        val detector = PlaybackEndDetector()
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
            if (detector.observe(state, trackUri, expectedTrackUri)) {
                AppLog.i("AppManagedPlaylist", "Track ended: $expectedTrackUri")
                return
            } else {
                AppLog.i("AppManagedPlaylist", "playbackEnded: false")
            }
            if (!detector.hasObservedPlayback() && ++startupPolls >= 15) {
                error("Renderer did not enter an active playback state")
            }
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
                    val session =
                        playbackSession.value
                            ?.takeIf { it.mode == PlaybackMode.NATIVE_PLAYLIST }
                            ?: return@launch
                    val index = session.videoFiles.indexOfFirst { it.url == trackUri }
                    if (index >= 0 && index != session.currentIndex) {
                        playbackSession.value = session.copy(currentIndex = index)
                    }
                }
            }
    }

    private fun cancelPlayback() {
        playbackJob?.cancel()
        nativeSyncJob?.cancel()
        playbackSession.value = null
    }
}

enum class PlaybackMode {
    NATIVE_PLAYLIST,
    APP_MANAGED,
}

data class PlaybackSession(
    val device: DlnaDevice,
    val mode: PlaybackMode,
    val videoFiles: List<VideoFile>,
    val currentIndex: Int,
    val nativePlaylist: DlnaMedia,
    val unsupportedCommands: Set<PlaybackCommand> = emptySet(),
    val transportState: TransportState = TransportState.PLAYING,
)

fun playlistIndexForCommand(
    currentIndex: Int,
    lastIndex: Int,
    command: PlaybackCommand,
): Int? =
    when (command) {
        PlaybackCommand.NEXT -> (currentIndex + 1).takeIf { it <= lastIndex }
        PlaybackCommand.PREVIOUS -> (currentIndex - 1).takeIf { it >= 0 }
        else -> null
    }
