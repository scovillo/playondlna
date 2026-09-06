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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    fun currentPositionSeconds(device: DlnaDevice): Double?

    fun positionInfo(device: DlnaDevice): PlaybackPositionInfo = PlaybackPositionInfo(currentTrackUri(device), currentPositionSeconds(device))

    fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    )
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
        val playbackTransport = transportFor(device)
        playbackTransport.play(playlist.url, playlist.metadataDidlLite())
        playbackTransport.mediaInfo()
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        transportFor(device).command(command)
    }

    override fun transportState(device: DlnaDevice): TransportState = transportFor(device).transportState()

    override fun currentTrackUri(device: DlnaDevice): String? = transportFor(device).currentTrackUri()

    override fun currentPositionSeconds(device: DlnaDevice): Double? = transportFor(device).currentPositionSeconds()

    override fun positionInfo(device: DlnaDevice): PlaybackPositionInfo = transportFor(device).positionInfo()

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) = transportFor(device).seekTo(seconds)
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
    private val isSponsorBlockEnabled: () -> Boolean = { false },
    private val sponsorBlockClient: SponsorBlockClient,
    private val playbackObservationDelay: Duration = 1.seconds,
) {
    private companion object {
        const val NATIVE_PLAYLIST_TERMINAL_POLLS = 10
        val ACTIVE_TRANSPORT_STATES =
            setOf(TransportState.PLAYING, TransportState.TRANSITIONING, TransportState.PAUSED_PLAYBACK)
        val TERMINAL_TRANSPORT_STATES = setOf(TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT)
    }

    private val playbackJobs = ConcurrentHashMap<String, Job>()
    private val playbackObservations = ConcurrentHashMap<String, PlaybackObservation>()
    private val playbackSessions = MutableStateFlow<Map<String, PlaybackSession>>(emptyMap())
    private val playlistPlaybackModes = MutableStateFlow<Map<String, PlaylistPlaybackMode>>(emptyMap())
    val activePlaylistPlaybackModes: StateFlow<Map<String, PlaylistPlaybackMode>> = playlistPlaybackModes.asStateFlow()

    fun playMedia(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        cancelPlayback(device)
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                if (device.avTransportUrl == null) {
                    AppLog.i("DlnaRemoteControl", "No avTransportUrl: ${device.friendlyName}")
                    onIncompatibleDevice(device)
                    return@launch
                }
                runCatching {
                    transport.playFile(device, item)
                    val observation = startPlaybackObservation(device)
                    monitorTrack(device, observation, item)
                }.onFailure {
                    if (it is CancellationException) throw it
                    onPlaybackFailure(it)
                }
            }
        registerPlaybackJob(device.usn, playbackJob)
    }

    fun clearPlaylistPlaybackModes() {
        playlistPlaybackModes.value = emptyMap()
    }

    fun playPlaylist(
        device: DlnaDevice,
        nativePlaylist: DlnaPlaylist,
        items: List<LibraryItem>,
        forcePlayOnDlnaManagedPlaylist: Boolean = false,
    ) {
        cancelPlayback(device)
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                if (device.avTransportUrl == null) {
                    onIncompatibleDevice(device)
                    return@launch
                }
                try {
                    if (!forcePlayOnDlnaManagedPlaylist && !isMixedPlaylist(items)) {
                        try {
                            AppLog.i("DlnaRemoteControl", "Trying native playlist for device: ${device.friendlyName}")
                            transport.playPlaylist(device, nativePlaylist)
                            val observation = startPlaybackObservation(device)
                            setPlaylistPlaybackMode(device, PlaylistPlaybackMode.PLAYER_MANAGED)
                            AppLog.i("DlnaRemoteControl", "Native playlist playback success on device: ${device.friendlyName}")
                            var finalTrackPlaybackObserved = false
                            var playbackObserved = false
                            var consecutiveTerminalPolls = 0
                            try {
                                monitorSponsorBlock(
                                    device,
                                    observation,
                                    stopWhen = { status ->
                                        val isFinalTrack =
                                            status.trackNumber?.let { it == items.size }
                                                ?: trackUrisMatch(status.trackUri, items.last().url)
                                        finalTrackPlaybackObserved =
                                            finalTrackPlaybackObserved ||
                                            (isFinalTrack && status.transportState in ACTIVE_TRANSPORT_STATES)
                                        playbackObserved =
                                            playbackObserved || status.transportState in ACTIVE_TRANSPORT_STATES
                                        consecutiveTerminalPolls =
                                            if (status.transportState in TERMINAL_TRANSPORT_STATES) {
                                                consecutiveTerminalPolls + 1
                                            } else {
                                                0
                                            }
                                        (finalTrackPlaybackObserved && status.transportState in TERMINAL_TRANSPORT_STATES) ||
                                            (playbackObserved && consecutiveTerminalPolls >= NATIVE_PLAYLIST_TERMINAL_POLLS)
                                    },
                                ) { status ->
                                    items.firstOrNull { trackUrisMatch(status.trackUri, it.url) }
                                        ?: status.trackNumber?.let { items.getOrNull(it - 1) }
                                }
                            } finally {
                                observation.stop()
                            }
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
                    }
                    if (forcePlayOnDlnaManagedPlaylist) {
                        AppLog.i("DlnaRemoteControl", "Force app managed playback for playlists is enabled")
                    }
                    if (isMixedPlaylist(items)) {
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
                    playAppPlaylistFrom(device.usn, 0)
                    AppLog.i("DlnaRemoteControl", "App managed playlist playback started on device: ${device.friendlyName}")
                } catch (_: CancellationException) {
                    // A newer command superseded this playback session.
                } catch (exception: Exception) {
                    onPlaybackFailure(exception)
                }
            }
        registerPlaybackJob(device.usn, playbackJob)
    }

    fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        val playbackSession = playbackSessions.value[device.usn]
        if (playbackSession == null) {
            sendCommand(device, command)
            return
        }
        val previousPlaybackJob = playbackJobs[device.usn]
        AppLog.i("AppManagedPlaylist", "Manual $command requested at index ${playbackSession.currentIndex}")
        previousPlaybackJob?.cancel()
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                try {
                    previousPlaybackJob?.join()
                    val currentSession = playbackSessions.value[device.usn] ?: return@launch
                    AppLog.i("AppManagedPlaylist", "Manual $command runs at index ${currentSession.currentIndex}")
                    when (command) {
                        PlaybackCommand.NEXT, PlaybackCommand.PREVIOUS -> {
                            changeTrack(currentSession, command)
                        }

                        PlaybackCommand.PLAY -> {
                            if (currentSession.transportState == TransportState.STOPPED) {
                                resumeStoppedSession(currentSession)
                            } else {
                                resumeSession(currentSession)
                            }
                        }

                        PlaybackCommand.PAUSE, PlaybackCommand.STOP -> {
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
                        markUnsupported(device.usn, command)
                    }
                    onPlaybackFailure(exception)
                }
            }
        registerPlaybackJob(device.usn, playbackJob)
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

    private suspend fun monitorSponsorBlock(
        device: DlnaDevice,
        observation: PlaybackObservation,
        stopWhen: ((PlaybackStatus) -> Boolean)? = null,
        itemForStatus: (PlaybackStatus) -> LibraryItem?,
    ) {
        if (!isSponsorBlockEnabled() && stopWhen == null) return
        val detectors = mutableMapOf<String, SponsorBlockSkipDetector>()
        observation.status.first { status ->
            if (isSponsorBlockEnabled()) {
                val item = itemForStatus(status)
                val position = status.positionSeconds
                if (item != null && position != null) {
                    val detector =
                        detectors.getOrPut(item.metadata.id) {
                            SponsorBlockSkipDetector(sponsorBlockClient.sponsorSegments(item.metadata.id))
                        }
                    val seekPosition = detector.nextSeekPosition(position)
                    if (seekPosition != null) {
                        AppLog.i("SponsorBlock", "Seeking mediaId=${item.metadata.id} from $position to $seekPosition")
                        runCatching { transport.seekTo(device, seekPosition) }.onFailure {
                            AppLog.w("SponsorBlock", "Seek unsupported for mediaId=${item.metadata.id}: ${it.message}")
                            throw CancellationException("Seek unsupported", it)
                        }
                    }
                }
            }

            stopWhen?.invoke(status) == true
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
            playAppPlaylistFrom(session.device.usn, index)
        }
    }

    private suspend fun resumeStoppedSession(session: PlaybackSession) {
        setPlaybackSession(
            session.copy(
                transportState = TransportState.PLAYING,
            ),
        )
        playAppPlaylistFrom(session.device.usn, session.currentIndex)
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
        deviceId: String,
        command: PlaybackCommand,
    ) {
        playbackSessions.update { sessions ->
            val session = sessions[deviceId] ?: return@update sessions
            sessions + (deviceId to session.copy(unsupportedCommands = session.unsupportedCommands + command))
        }
    }

    private suspend fun playAppPlaylistFrom(
        deviceId: String,
        startIndex: Int,
    ) {
        var session = playbackSessions.value[deviceId] ?: return
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
            val observation = startPlaybackObservation(session.device)
            monitorTrack(session.device, observation, item)
        }
    }

    private suspend fun monitorTrack(
        device: DlnaDevice,
        observation: PlaybackObservation,
        item: LibraryItem,
    ) {
        coroutineScope {
            val sponsorBlockJob = launch { monitorSponsorBlock(device, observation) { item } }
            try {
                awaitPlaybackEnd(observation, item.url)
            } finally {
                sponsorBlockJob.cancelAndJoin()
                observation.stop()
            }
        }
    }

    private suspend fun continueAppPlaylist(session: PlaybackSession) {
        val observation = startPlaybackObservation(session.device)
        monitorTrack(session.device, observation, session.items[session.currentIndex])
        if (session.currentIndex < session.items.lastIndex) {
            playAppPlaylistFrom(session.device.usn, session.currentIndex + 1)
        }
    }

    private suspend fun awaitPlaybackEnd(
        observation: PlaybackObservation,
        expectedTrackUri: String,
    ) {
        val detector = PlaybackEndDetector()
        var startupPolls = 0
        var previousState: TransportState? = null
        observation.status
            .onEach { status ->
                if (status.transportState != previousState) {
                    AppLog.i("AppManagedPlaylist", "Track state: ${status.transportState}, URI: ${status.trackUri}")
                    previousState = status.transportState
                }
            }
            .first { status ->
                detector.observe(
                    status.transportState,
                    status.trackUri,
                    expectedTrackUri,
                    status.positionSeconds,
                    status.durationSeconds,
                ).also { ended ->
                    if (ended) AppLog.i("AppManagedPlaylist", "Track ended: $expectedTrackUri")
                    if (!detector.hasObservedPlayback() && ++startupPolls >= 15) {
                        error("Renderer did not enter an active playback state")
                    }
                }
            }
    }

    private fun registerPlaybackJob(
        deviceId: String,
        playbackJob: Job,
    ) {
        playbackJobs[deviceId] = playbackJob
        playbackJob.invokeOnCompletion { playbackJobs.remove(deviceId, playbackJob) }
    }

    private fun setPlaybackSession(session: PlaybackSession) {
        playbackSessions.update { it + (session.device.usn to session) }
    }

    private fun setPlaylistPlaybackMode(
        device: DlnaDevice,
        mode: PlaylistPlaybackMode,
    ) {
        playlistPlaybackModes.update { it + (device.usn to mode) }
    }

    /** Replaces only this renderer's poller; observations for other renderers keep running. */
    private fun startPlaybackObservation(device: DlnaDevice): PlaybackObservation {
        val deviceId = device.usn
        val observation = PlaybackObservation(scope, device, transport, playbackObservationDelay)
        playbackObservations.put(deviceId, observation)?.stop()
        observation.start()
        return observation
    }

    private fun cancelPlayback(device: DlnaDevice) {
        val deviceId = device.usn
        playbackJobs.remove(deviceId)?.cancel()
        playbackObservations[deviceId]?.stop()
        playbackSessions.update { it - deviceId }
        playlistPlaybackModes.update { it - deviceId }
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
