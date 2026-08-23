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

package io.github.scovillo.playondlna.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.server.VideoFile
import io.github.scovillo.playondlna.ui.ToastEvent
import io.github.scovillo.playondlna.upnpdlna.DlnaDevice
import io.github.scovillo.playondlna.upnpdlna.DlnaMedia
import io.github.scovillo.playondlna.upnpdlna.FavoriteDevices
import io.github.scovillo.playondlna.upnpdlna.SsdpDevices
import io.github.scovillo.playondlna.upnpdlna.TransportCommand
import io.github.scovillo.playondlna.upnpdlna.TransportState
import io.github.scovillo.playondlna.upnpdlna.currentTrackUri
import io.github.scovillo.playondlna.upnpdlna.isUnsupportedActionError
import io.github.scovillo.playondlna.upnpdlna.isUnsupportedPlaylistError
import io.github.scovillo.playondlna.upnpdlna.isUnsupportedTrackSeekError
import io.github.scovillo.playondlna.upnpdlna.playUriOnDevice
import io.github.scovillo.playondlna.upnpdlna.seekToTrack
import io.github.scovillo.playondlna.upnpdlna.sendTransportCommand
import io.github.scovillo.playondlna.upnpdlna.startingAt
import io.github.scovillo.playondlna.upnpdlna.transportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class DlnaDevicesListScreenModel(
    private val ssdpDevices: SsdpDevices,
    val favoriteDevices: FavoriteDevices,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private var playbackJob: Job? = null
    private var nativeSyncJob: Job? = null
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playbackSession = MutableStateFlow<PlaybackSession?>(null)
    val playbackSession: StateFlow<PlaybackSession?> = _playbackSession.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>()
    val toastEvents = merge(_toastEvents.asSharedFlow(), ssdpDevices.toastEvents)

    fun discoverDevices() {
        viewModelScope.launch {
            _isLoading.value = true
            _devices.value = emptyList()
            val jobs =
                listOf(
                    launch(Dispatchers.IO) {
                        val ssdp = ssdpDevices.discover()
                        _devices.update { it + ssdp }
                    },
                    launch(Dispatchers.IO) {
                        val manual = favoriteDevices.discover()
                        _devices.update { it + manual }
                    },
                )
            jobs.joinAll()
            _devices.update { it.distinctBy { device -> device.location } }
            _isLoading.value = false
        }
    }

    fun selectDevice(device: DlnaDevice) {
        _selectedDevice.value = device
    }

    fun playVideoOnDevice(
        device: DlnaDevice,
        videoFile: VideoFile,
    ) {
        playbackJob?.cancel()
        nativeSyncJob?.cancel()
        _playbackSession.value = null
        playMediaOnDevice(device, DlnaMedia(videoFile.url, videoFile.metaData))
    }

    fun playPlaylistOnDevice(
        device: DlnaDevice,
        nativePlaylist: DlnaMedia,
        videoFiles: List<VideoFile>,
    ) {
        playbackJob?.cancel()
        nativeSyncJob?.cancel()
        playbackJob =
            viewModelScope.launch(Dispatchers.IO) {
                val avTransportUrl = device.avTransportUrl
                if (avTransportUrl == null) {
                    incompatibleDevice(device)
                    return@launch
                }
                try {
                    val nativeSupport = settingsRepository.nativePlaylistSupport(device.usn)
                    if (nativeSupport != false) {
                        try {
                            playUriOnDevice(avTransportUrl, nativePlaylist)
                            settingsRepository.saveNativePlaylistSupport(device.usn, true)
                            _playbackSession.value =
                                PlaybackSession(device, PlaybackMode.NATIVE_PLAYLIST, videoFiles, 0, nativePlaylist)
                            startNativePlaylistSync(avTransportUrl)
                            return@launch
                        } catch (exception: Exception) {
                            if (!isUnsupportedPlaylistError(exception)) throw exception
                            Log.w("playPlaylistOnDevice", "Native playlist rejected, using app queue", exception)
                            settingsRepository.saveNativePlaylistSupport(device.usn, false)
                        }
                    }
                    _playbackSession.value =
                        PlaybackSession(device, PlaybackMode.APP_MANAGED, videoFiles, 0, nativePlaylist)
                    playAppPlaylistFrom(0)
                } catch (_: CancellationException) {
                    Log.d("playPlaylistOnDevice", "Playlist playback control cancelled")
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    _toastEvents.emit(ToastEvent.Show(R.string.playback_failed))
                }
            }
    }

    fun remoteCommand(command: TransportCommand) {
        val selectedDevice = _selectedDevice.value ?: return
        val session = _playbackSession.value?.takeIf { it.device.location == selectedDevice.location }
        if (session == null) {
            sendRemoteCommandToSelectedDevice(selectedDevice, command)
            return
        }
        playbackJob?.cancel()
        playbackJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    when {
                        command == TransportCommand.NEXT || command == TransportCommand.PREVIOUS -> {
                            val index =
                                playlistIndexForCommand(session.currentIndex, session.videoFiles.lastIndex, command)
                                    ?: return@launch
                            if (session.transportState == TransportState.STOPPED) {
                                _playbackSession.value = session.copy(currentIndex = index)
                            } else if (session.mode == PlaybackMode.APP_MANAGED) {
                                playAppPlaylistFrom(index)
                            } else {
                                sendTransportCommand(session.device.avTransportUrl!!, command)
                                _playbackSession.value = session.copy(currentIndex = index)
                            }
                        }

                        command == TransportCommand.PLAY && session.transportState == TransportState.STOPPED -> {
                            if (session.mode == PlaybackMode.NATIVE_PLAYLIST) {
                                val avTransportUrl = session.device.avTransportUrl!!
                                try {
                                    seekToTrack(avTransportUrl, session.currentIndex + 1)
                                    sendTransportCommand(avTransportUrl, command)
                                } catch (exception: Exception) {
                                    if (!isUnsupportedTrackSeekError(exception)) throw exception
                                    playUriOnDevice(avTransportUrl, session.nativePlaylist.startingAt(session.currentIndex))
                                }
                                _playbackSession.value = session.copy(transportState = TransportState.PLAYING)
                            } else {
                                _playbackSession.value = session.copy(transportState = TransportState.PLAYING)
                                playAppPlaylistFrom(session.currentIndex)
                            }
                        }

                        command == TransportCommand.PLAY -> {
                            sendTransportCommand(session.device.avTransportUrl!!, command)
                            _playbackSession.value = session.copy(transportState = TransportState.PLAYING)
                            if (session.mode == PlaybackMode.APP_MANAGED) continueAppPlaylist(session)
                        }

                        command == TransportCommand.PAUSE || command == TransportCommand.STOP -> {
                            sendTransportCommand(session.device.avTransportUrl!!, command)
                            _playbackSession.value =
                                session.copy(
                                    transportState =
                                        if (command == TransportCommand.STOP) {
                                            TransportState.STOPPED
                                        } else {
                                            TransportState.PAUSED_PLAYBACK
                                        },
                                )
                        }
                    }
                } catch (_: CancellationException) {
                    Log.d("DlnaRemote", "Remote command cancelled")
                } catch (exception: Exception) {
                    if (isUnsupportedActionError(exception)) {
                        val currentSession = _playbackSession.value
                        if (currentSession != null) {
                            _playbackSession.value =
                                currentSession.copy(unsupportedCommands = currentSession.unsupportedCommands + command)
                        }
                    }
                    exception.printStackTrace()
                    _toastEvents.emit(ToastEvent.Show(R.string.playback_failed))
                }
            }
    }

    private fun sendRemoteCommandToSelectedDevice(
        device: DlnaDevice,
        command: TransportCommand,
    ) {
        val avTransportUrl = device.avTransportUrl
        if (avTransportUrl == null) {
            viewModelScope.launch { incompatibleDevice(device) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendTransportCommand(avTransportUrl, command)
            } catch (exception: Exception) {
                Log.e("DlnaRemote", "Could not send $command to ${device.friendlyName}", exception)
                _toastEvents.emit(ToastEvent.Show(R.string.playback_failed))
            }
        }
    }

    fun playMediaOnDevice(
        device: DlnaDevice,
        media: DlnaMedia,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (device.avTransportUrl != null) {
                try {
                    Log.d("playVideoOnDevice", "Send playback command to ${device.avTransportUrl}")
                    playUriOnDevice(device.avTransportUrl, media)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    _toastEvents.emit(ToastEvent.Show(R.string.playback_failed))
                }
            } else {
                incompatibleDevice(device)
            }
        }
    }

    private suspend fun awaitPlaybackEnd(avTransportUrl: String) {
        var playbackObserved = false
        var startupPolls = 0
        while (true) {
            delay(1000)
            when (getTransportStateWithRetry(avTransportUrl)) {
                TransportState.PLAYING,
                TransportState.TRANSITIONING,
                TransportState.PAUSED_PLAYBACK,
                -> playbackObserved = true

                TransportState.STOPPED,
                TransportState.NO_MEDIA_PRESENT,
                -> if (playbackObserved) return

                TransportState.UNKNOWN -> Unit
            }
            if (!playbackObserved && ++startupPolls >= 15) {
                throw Exception("Renderer did not enter an active playback state")
            }
        }
    }

    private suspend fun getTransportStateWithRetry(avTransportUrl: String): TransportState {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return transportState(avTransportUrl)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                lastError = exception
                if (attempt < 2) delay(500)
            }
        }
        throw lastError ?: Exception("GetTransportInfo failed")
    }

    private suspend fun playAppPlaylistFrom(startIndex: Int) {
        var session = _playbackSession.value ?: return
        for (index in startIndex..session.videoFiles.lastIndex) {
            session = session.copy(currentIndex = index)
            _playbackSession.value = session
            val videoFile = session.videoFiles[index]
            Log.d("playPlaylistOnDevice", "Playing ${index + 1}/${session.videoFiles.size}: ${videoFile.id}")
            playUriOnDevice(session.device.avTransportUrl!!, videoFile)
            if (index < session.videoFiles.lastIndex) awaitPlaybackEnd(session.device.avTransportUrl)
        }
    }

    private suspend fun continueAppPlaylist(session: PlaybackSession) {
        if (session.currentIndex >= session.videoFiles.lastIndex) return
        awaitPlaybackEnd(session.device.avTransportUrl!!)
        playAppPlaylistFrom(session.currentIndex + 1)
    }

    private fun startNativePlaylistSync(avTransportUrl: String) {
        nativeSyncJob?.cancel()
        nativeSyncJob =
            viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(2000)
                    val trackUri = runCatching { currentTrackUri(avTransportUrl) }.getOrNull() ?: continue
                    val session = _playbackSession.value?.takeIf { it.mode == PlaybackMode.NATIVE_PLAYLIST } ?: return@launch
                    val index = session.videoFiles.indexOfFirst { it.url == trackUri }
                    if (index >= 0 && index != session.currentIndex) {
                        _playbackSession.value = session.copy(currentIndex = index)
                    }
                }
            }
    }

    private suspend fun incompatibleDevice(device: DlnaDevice) {
        Log.e(
            "playVideoOnDevice",
            "No AVTransport URL found for ${device.friendlyName} @ ${device.location}",
        )
        _toastEvents.emit(ToastEvent.Show(R.string.player_incompatible))
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
