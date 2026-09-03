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
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.dlna.soap.SoapPlaybackTransportFactory
import io.github.scovillo.playondlna.dlna.soap.UpnpActionException
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.ui.DlnaRemoteControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** Transport boundary used by [DlnaRemoteControl] - provides legacy compatibility layer */
interface DlnaTransport {
    fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    )

    fun playPlaylist(
        device: DlnaDevice,
        playlist: DlnaPlaylist,
    )

    fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    )

    fun transportState(device: DlnaDevice): TransportState

    fun currentTrackUri(device: DlnaDevice): String?
}

/**
 * Adapter from DlnaTransport to PlaybackTransport.
 * Creates appropriate PlaybackTransport implementations for communication with a device.
 */
class SoapDlnaTransport : DlnaTransport {
    private val transportFactory = SoapPlaybackTransportFactory()

    private fun transportFor(device: DlnaDevice): PlaybackTransport = transportFactory.create(device.requireAvTransportUrl())

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        transportFor(device).play(item.url, item.metaDataDidlLite)
    }

    override fun playPlaylist(
        device: DlnaDevice,
        playlist: DlnaPlaylist,
    ) {
        transportFor(device).play(playlist.url, playlist.metadataDidlLite())
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        transportFor(device).command(command)
    }

    override fun transportState(device: DlnaDevice): TransportState = transportFor(device).transportState()

    override fun currentTrackUri(device: DlnaDevice): String? = transportFor(device).currentTrackUri()
}

/**
 * Controls independent DLNA playback sessions without any dependency on Android UI or persistence.
 *
 * The host supplies persistence and user-feedback callbacks, while the transport can be replaced
 * in tests or by another UPnP implementation.
 */
class DlnaRemoteControl(
    private val scope: CoroutineScope,
    private val transport: DlnaTransport = SoapDlnaTransport(),
    private val onIncompatibleDevice: suspend (DlnaDevice) -> Unit = {},
    private val onPlaybackFailure: suspend (Throwable) -> Unit = {},
) {
    private val playbackJobs = ConcurrentHashMap<String, Job>()
    private val playbackSessions = MutableStateFlow<Map<String, PlaybackSession>>(emptyMap())
    private val playlistPlaybackModes = MutableStateFlow<Map<String, PlaylistPlaybackMode>>(emptyMap())
    val activePlaylistPlaybackModes: StateFlow<Map<String, PlaylistPlaybackMode>> = playlistPlaybackModes.asStateFlow()

    fun playVideo(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        cancelPlayback(device)
        scope.launch(Dispatchers.IO) {
            if (device.avTransportUrl == null) {
                onIncompatibleDevice(device)
                return@launch
            }
            runCatching { transport.playFile(device, item) }.onFailure { onPlaybackFailure(it) }
        }
    }

    fun clearPlaylistPlaybackModes() {
        playlistPlaybackModes.value = emptyMap()
    }

    fun playPlaylist(
        device: DlnaDevice,
        nativePlaylist: DlnaPlaylist,
        items: List<LibraryItem>,
    ) {
        cancelPlayback(device)
        val deviceKey = device.location
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                if (device.avTransportUrl == null) {
                    onIncompatibleDevice(device)
                    return@launch
                }
                try {
                    if (!isMixedPlaylist(items)) {
                        try {
                            AppLog.i("DlnaRemoteControl", "Trying native playlist for device: ${device.friendlyName}")
                            transport.playPlaylist(device, nativePlaylist)
                            setPlaylistPlaybackMode(device, PlaylistPlaybackMode.PLAYER_MANAGED)
                            AppLog.i("DlnaRemoteControl", "Native playlist playback success on device: ${device.friendlyName}")
                            return@launch
                        } catch (exception: Exception) {
                            if (
                                !UpnpActionException.isUnsupportedPlaylist(
                                    exception,
                                )
                            ) {
                                throw exception
                            }
                            AppLog.i("DlnaRemoteControl", "Native playlist playback failed on device: ${device.friendlyName}")
                        }
                    } else {
                        AppLog.i("DlnaRemoteControl", "Using app managed playback for mixed playlist")
                    }
                    currentCoroutineContext().ensureActive()
                    setPlaybackSession(
                        PlaybackSession(
                            device,
                            items,
                            0,
                        ),
                    )
                    setPlaylistPlaybackMode(device, PlaylistPlaybackMode.PLAY_ON_DLNA_MANAGED)
                    playAppPlaylistFrom(deviceKey, 0)
                    AppLog.i("DlnaRemoteControl", "App managed playlist playback started on device: ${device.friendlyName}")
                } catch (_: CancellationException) {
                    // A newer command superseded this playback session.
                } catch (exception: Exception) {
                    onPlaybackFailure(exception)
                }
            }
        registerPlaybackJob(deviceKey, playbackJob)
    }

    fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        val deviceKey = device.location
        val playbackSession = playbackSessions.value[deviceKey]
        if (playbackSession == null) {
            sendCommand(device, command)
            return
        }
        val previousPlaybackJob = playbackJobs[deviceKey]
        AppLog.i("AppManagedPlaylist", "Manual $command requested at index ${playbackSession.currentIndex}")
        previousPlaybackJob?.cancel()
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                try {
                    previousPlaybackJob?.join()
                    val currentSession = playbackSessions.value[deviceKey] ?: return@launch
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
                        markUnsupported(deviceKey, command)
                    }
                    onPlaybackFailure(exception)
                }
            }
        registerPlaybackJob(deviceKey, playbackJob)
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
        val index = playlistIndexForCommand(session.currentIndex, session.items.lastIndex, command) ?: return
        if (session.transportState == TransportState.STOPPED) {
            setPlaybackSession(session.copy(currentIndex = index))
        } else {
            playAppPlaylistFrom(session.device.location, index)
        }
    }

    private suspend fun resumeStoppedSession(session: PlaybackSession) {
        setPlaybackSession(
            session.copy(
                transportState = TransportState.PLAYING,
            ),
        )
        playAppPlaylistFrom(session.device.location, session.currentIndex)
    }

    private suspend fun resumeSession(session: PlaybackSession) {
        transport.command(session.device, PlaybackCommand.PLAY)
        setPlaybackSession(
            session.copy(
                transportState = TransportState.PLAYING,
            ),
        )
        continueAppPlaylist(session)
    }

    private fun pauseOrStop(
        session: PlaybackSession,
        command: PlaybackCommand,
    ) {
        transport.command(session.device, command)
        setPlaybackSession(
            session.copy(
                transportState =
                    if (command == PlaybackCommand.STOP) {
                        TransportState.STOPPED
                    } else {
                        TransportState.PAUSED_PLAYBACK
                    },
            ),
        )
    }

    private fun markUnsupported(
        deviceKey: String,
        command: PlaybackCommand,
    ) {
        playbackSessions.update { sessions ->
            val session = sessions[deviceKey] ?: return@update sessions
            sessions + (deviceKey to session.copy(unsupportedCommands = session.unsupportedCommands + command))
        }
    }

    private suspend fun playAppPlaylistFrom(
        deviceKey: String,
        startIndex: Int,
    ) {
        var session = playbackSessions.value[deviceKey] ?: return
        AppLog.i("AppManagedPlaylist", "Continue from index $startIndex of ${session.items.size}")
        for (index in startIndex..session.items.lastIndex) {
            currentCoroutineContext().ensureActive()
            session = session.copy(currentIndex = index)
            setPlaybackSession(session)
            val item = session.items[index]
            AppLog.i("AppManagedPlaylist", "Start ${index + 1}/${session.items.size}: ${item.metadata.id}")
            try {
                transport.playFile(session.device, item)
            } catch (exception: Exception) {
                if (
                    !UpnpActionException.isTransitionInProgress(
                        exception,
                    )
                ) {
                    throw exception
                }
                AppLog.i("AppManagedPlaylist", "Renderer is already transitioning to ${index + 1}/${session.items.size}")
            }
            currentCoroutineContext().ensureActive()
            if (index < session.items.lastIndex) awaitPlaybackEnd(session.device, item.url)
        }
    }

    private suspend fun continueAppPlaylist(session: PlaybackSession) {
        if (session.currentIndex >= session.items.lastIndex) return
        awaitPlaybackEnd(session.device, session.items[session.currentIndex].url)
        playAppPlaylistFrom(session.device.location, session.currentIndex + 1)
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

    private fun registerPlaybackJob(
        deviceKey: String,
        playbackJob: Job,
    ) {
        playbackJobs[deviceKey] = playbackJob
        playbackJob.invokeOnCompletion { playbackJobs.remove(deviceKey, playbackJob) }
    }

    private fun setPlaybackSession(session: PlaybackSession) {
        playbackSessions.update { it + (session.device.location to session) }
    }

    private fun setPlaylistPlaybackMode(
        device: DlnaDevice,
        mode: PlaylistPlaybackMode,
    ) {
        playlistPlaybackModes.update { it + (device.location to mode) }
    }

    private fun cancelPlayback(device: DlnaDevice) {
        val deviceKey = device.location
        playbackJobs.remove(deviceKey)?.cancel()
        playbackSessions.update { it - deviceKey }
        playlistPlaybackModes.update { it - deviceKey }
    }
}

enum class PlaylistPlaybackMode {
    PLAY_ON_DLNA_MANAGED,
    PLAYER_MANAGED,
}

data class PlaybackSession(
    val device: DlnaDevice,
    val items: List<LibraryItem>,
    val currentIndex: Int,
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

fun isMixedPlaylist(videoFiles: List<LibraryItem>): Boolean {
    val metadata = videoFiles.map { it.metadata }
    return metadata.any(LibraryMetadata::isAudioOnly) && metadata.any { !it.isAudioOnly }
}
