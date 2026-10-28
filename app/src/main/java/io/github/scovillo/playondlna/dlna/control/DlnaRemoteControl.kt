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
import io.github.scovillo.playondlna.dlna.soap.UpnpActionException
import io.github.scovillo.playondlna.model.LibraryItem
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
    @Volatile
    private var selectedDeviceId: String? = null
    private val backgroundPlaylists = ConcurrentHashMap.newKeySet<String>()
    private val observationRequests = ConcurrentHashMap<String, Job>()
    private val statusCollectors = ConcurrentHashMap<String, Job>()
    private val playbackJobs = ConcurrentHashMap<String, Job>()
    private val playbackObservations = ConcurrentHashMap<String, PlaybackObservation>()
    private val _playbackStatuses = MutableStateFlow<Map<String, PlaybackStatus>>(emptyMap())
    val playbackStatuses: StateFlow<Map<String, PlaybackStatus>> = _playbackStatuses.asStateFlow()
    private val _playlistIndices = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playlistIndices: StateFlow<Map<String, Int>> = _playlistIndices.asStateFlow()
    private val playbackSessions = MutableStateFlow<Map<String, PlaybackSession>>(emptyMap())

    /** Only managed playlists retain observation when another renderer is selected. */
    private fun selectObservationDevice(device: DlnaDevice) {
        selectedDeviceId = device.usn
        val otherDevices = playbackObservations.keys + playbackJobs.keys + observationRequests.keys
        otherDevices.filter { it != device.usn && it !in backgroundPlaylists }.forEach { deviceId ->
            observationRequests.remove(deviceId)?.cancel()
            playbackJobs.remove(deviceId)?.cancel()
            playbackObservations[deviceId]?.cancel()
            statusCollectors.remove(deviceId)?.cancel()
        }
    }

    fun observeDevice(device: DlnaDevice) {
        selectObservationDevice(device)
        if (device.avTransportUrl == null || playbackJobs[device.usn]?.isActive == true) return
        observationRequests.remove(device.usn)?.cancel()
        val request =
            scope.launch(Dispatchers.IO) {
                if (selectedDeviceId != device.usn || playbackJobs[device.usn]?.isActive == true) return@launch
                startPlaybackObservation(device).stopOnPlayerStop()
            }
        observationRequests[device.usn] = request
        request.invokeOnCompletion { observationRequests.remove(device.usn, request) }
    }

    fun playMedia(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        selectObservationDevice(device)
        cancelPlayback(device)
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                if (device.avTransportUrl == null) {
                    AppLog.i("DlnaRemoteControl", "No avTransportUrl: ${device.friendlyName}")
                    onIncompatibleDevice(device)
                    return@launch
                }
                runCatching {
                    startMedia(device, item)
                    val observation = startPlaybackObservation(device)
                    monitorTrack(device, observation, item)
                }.onFailure {
                    if (it is CancellationException) throw it
                    onPlaybackFailure(it)
                }
            }
        registerPlaybackJob(device.usn, playbackJob)
    }

    fun playPlaylist(
        device: DlnaDevice,
        items: List<LibraryItem>,
    ) {
        selectObservationDevice(device)
        cancelPlayback(device)
        backgroundPlaylists.add(device.usn)
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                if (device.avTransportUrl == null) {
                    onIncompatibleDevice(device)
                    return@launch
                }
                try {
                    AppLog.i("DlnaRemoteControl", "Using app managed playback for playlist on device: ${device.friendlyName}")
                    currentCoroutineContext().ensureActive()
                    setPlaybackSession(
                        PlaybackSession(
                            device,
                            items,
                            0,
                        ),
                    )
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
        if (command != PlaybackCommand.STOP) {
            previousPlaybackJob?.cancel()
        } else {
            playbackObservations[device.usn]?.stopOnPlayerStop()
        }
        val playbackJob =
            scope.launch(Dispatchers.IO) {
                try {
                    if (command != PlaybackCommand.STOP) {
                        previousPlaybackJob?.join()
                    }
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

    fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) {
        scope.launch(Dispatchers.IO) {
            val status = _playbackStatuses.value[device.usn] ?: return@launch
            if (status.transportState != TransportState.PLAYING && status.transportState != TransportState.PAUSED_PLAYBACK) return@launch
            val duration = status.durationSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return@launch
            if (!seconds.isFinite()) return@launch
            val boundedSeconds = seconds.coerceIn(0.0, duration)
            runCatching { transport.seekTo(device, boundedSeconds) }.onFailure { onPlaybackFailure(it) }
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
            runCatching {
                transport.command(device, command)
                if (command == PlaybackCommand.STOP) playbackObservations[device.usn]?.stopOnPlayerStop()
                if (
                    command == PlaybackCommand.PLAY &&
                    (playbackJobs[device.usn]?.isActive != true || playbackObservations[device.usn]?.isStopRequested == true)
                ) {
                    playbackJobs.remove(device.usn)?.cancelAndJoin()
                    startPlaybackObservation(device).stopOnPlayerStop()
                }
            }.onFailure { onPlaybackFailure(it) }
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
        val index = session.nextIndex(command)
        if (index == session.currentIndex) {
            return
        }
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
        backgroundPlaylists.add(deviceId)
        AppLog.i("AppManagedPlaylist", "Continue from index $startIndex of ${session.items.size}")
        for (index in startIndex..session.items.lastIndex) {
            currentCoroutineContext().ensureActive()
            session = session.copy(currentIndex = index, transportState = TransportState.PLAYING)
            val item = session.items[index]
            AppLog.i("AppManagedPlaylist", "Start ${index + 1}/${session.items.size}: ${item.metadata.id}")
            startMedia(session.device, item)
            setPlaybackSession(session)
            currentCoroutineContext().ensureActive()
            val observation = startPlaybackObservation(session.device)
            val reachedFileEnd = monitorTrack(session.device, observation, item)
            if (!reachedFileEnd || index == session.items.lastIndex) {
                markSessionStopped(session.device.usn)
                return
            }
        }
    }

    private fun startMedia(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        try {
            transport.playFile(device, item)
        } catch (exception: Exception) {
            if (!UpnpActionException.isTransitionInProgress(exception)) throw exception
            // Some renderers start loading on SetAVTransportURI and reject the immediate Play.
            // Keep observing; awaitPlaybackEnd still requires an active playback state.
            AppLog.i("DlnaRemoteControl", "Play returned 701; checking playback state for ${item.metadata.id}")
        }
    }

    private suspend fun monitorTrack(
        device: DlnaDevice,
        observation: PlaybackObservation,
        item: LibraryItem,
    ): Boolean =
        coroutineScope {
            val sponsorBlockJob = launch { monitorSponsorBlock(device, observation) { item } }
            try {
                awaitPlaybackEnd(observation, item)
            } finally {
                sponsorBlockJob.cancelAndJoin()
                observation.cancel()
            }
        }

    private fun markSessionStopped(deviceId: String) {
        backgroundPlaylists.remove(deviceId)
        playbackSessions.value[deviceId]?.let { setPlaybackSession(it.copy(transportState = TransportState.STOPPED)) }
    }

    private suspend fun continueAppPlaylist(session: PlaybackSession) {
        backgroundPlaylists.add(session.device.usn)
        val observation = startPlaybackObservation(session.device)
        val reachedFileEnd = monitorTrack(session.device, observation, session.items[session.currentIndex])
        if (reachedFileEnd && session.currentIndex < session.items.lastIndex) {
            playAppPlaylistFrom(session.device.usn, session.currentIndex + 1)
        } else {
            markSessionStopped(session.device.usn)
        }
    }

    private suspend fun awaitPlaybackEnd(
        observation: PlaybackObservation,
        item: LibraryItem,
    ): Boolean {
        val expectedTrackUri = item.url
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
                    status.durationSeconds?.takeIf { it > 0.0 }
                        ?: item.metadata.durationInSeconds.toDouble().takeIf { it > 0.0 },
                ).let { ended ->
                    ended || (
                        observation.isStopRequested &&
                            status.transportState in setOf(TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT)
                    )
                }.also { ended ->
                    if (ended) AppLog.i("AppManagedPlaylist", "Track ended: $expectedTrackUri")
                    if (!ended && !detector.hasObservedPlayback() && ++startupPolls >= 15) {
                        error("Renderer did not enter an active playback state")
                    }
                }
            }
        return detector.hasReachedFileEnd() && !observation.isStopRequested
    }

    private fun registerPlaybackJob(
        deviceId: String,
        playbackJob: Job,
    ) {
        playbackJobs[deviceId] = playbackJob
        playbackJob.invokeOnCompletion {
            if (playbackJobs.remove(deviceId, playbackJob)) backgroundPlaylists.remove(deviceId)
        }
    }

    private fun setPlaybackSession(session: PlaybackSession) {
        playbackSessions.update { it + (session.device.usn to session) }
        _playlistIndices.update { it + (session.device.usn to session.currentIndex) }
    }

    /** Replaces only this renderer's poller; observations for other renderers keep running. */
    private suspend fun startPlaybackObservation(device: DlnaDevice): PlaybackObservation {
        val deviceId = device.usn
        playbackObservations[deviceId]?.cancelAndJoin()
        currentCoroutineContext().ensureActive()
        if (selectedDeviceId != deviceId && deviceId !in backgroundPlaylists) throw CancellationException("Renderer no longer selected")
        val observation = PlaybackObservation(scope, device, transport, playbackObservationDelay)
        playbackObservations.put(deviceId, observation)?.cancel()
        statusCollectors.remove(deviceId)?.cancel()
        statusCollectors[deviceId] =
            scope.launch {
                observation.status.collect { status ->
                    _playbackStatuses.update { it + (deviceId to status) }
                }
            }
        observation.start()
        return observation
    }

    private fun cancelPlayback(device: DlnaDevice) {
        val deviceId = device.usn
        observationRequests.remove(deviceId)?.cancel()
        backgroundPlaylists.remove(deviceId)
        playbackJobs.remove(deviceId)?.cancel()
        playbackObservations[deviceId]?.cancel()
        statusCollectors.remove(deviceId)?.cancel()
        _playbackStatuses.update { it - deviceId }
        _playlistIndices.update { it - deviceId }
        playbackSessions.update { it - deviceId }
    }
}
