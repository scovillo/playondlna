package io.github.scovillo.playondlna.dlna.observation

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.transport.DlnaTransport
import io.github.scovillo.playondlna.dlna.transport.PlaybackCommand
import io.github.scovillo.playondlna.dlna.transport.PlaybackPositionInfo
import io.github.scovillo.playondlna.dlna.transport.TransportState
import io.github.scovillo.playondlna.dlna.transport.soap.SoapDlnaTransport
import io.github.scovillo.playondlna.model.Playlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PlaybackSession(
    val device: DlnaDevice,
    val playlist: Playlist,
    val currentIndex: Int,
    val currentState: TransportState = TransportState.UNKNOWN,
    val positionInfo: PlaybackPositionInfo = PlaybackPositionInfo(),
) {
    val id
        get() = device.usn

    val lastIndex: Int
        get() = playlist.items.lastIndex

    val currentItem
        get() = playlist.items.getOrNull(currentIndex)

    val hasNext: Boolean
        get() = currentIndex in playlist.items.indices && currentIndex < lastIndex

    val hasPrevious: Boolean
        get() = currentIndex in playlist.items.indices && currentIndex > 0

    val currentPosition: Double
        get() = positionInfo.positionSeconds

    fun predictIndex(command: PlaybackCommand): Int =
        when (command) {
            PlaybackCommand.NEXT -> (currentIndex + 1).takeIf { it <= lastIndex } ?: currentIndex
            PlaybackCommand.PREVIOUS -> (currentIndex - 1).takeIf { it >= 0 } ?: currentIndex
            else -> currentIndex
        }

    fun copy(
        state: TransportState,
        positionInfo: PlaybackPositionInfo,
    ): PlaybackSession {
        val index =
            playlist.items.indexOfFirst {
                positionInfo.trackUri.contains(it.metadata.id)
            }
        return PlaybackSession(device, playlist, index, state, positionInfo)
    }

    override fun toString(): String {
        return "PlaybackSession(device=${device.friendlyName}, playlist=$playlist, currentIndex=$currentIndex, currentState=$currentState, positionInfo=$positionInfo)"
    }
}

class PlaybackSessions(
    private val transport: DlnaTransport = SoapDlnaTransport(),
    val delay: Duration = 1.seconds,
) {
    val all = ConcurrentHashMap<String, PlaybackSession>()
    private val _updates = MutableSharedFlow<PlaybackSession>()
    val updates = _updates.asSharedFlow()

    fun register(session: PlaybackSession) = all.computeIfAbsent(session.id, { session })

    fun update(
        id: String,
        playlist: Playlist,
    ) {
        all.computeIfPresent(id) { _, current ->
            current.copy(playlist = playlist, currentIndex = 0)
        }
    }

    fun has(id: String) = all.containsKey(id)

    fun get(id: String) = all[id]!!

    fun remove(id: String) = all.remove(id)

    suspend fun observe() =
        withContext(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                supervisorScope {
                    all.values.toList().forEach { session ->
                        launch {
                            try {
                                val info = transport.playbackInfo(session.device)
                                currentCoroutineContext().ensureActive()
                                val updated =
                                    all.computeIfPresent(session.id) { _, current ->
                                        current.copy(state = info.state, positionInfo = info.positionInfo)
                                    }
                                updated?.let { _updates.emit(it) }
                                AppLog.d("PlaybackSessions", "Updated ${session.device.friendlyName} to $updated")
                            } catch (exception: Exception) {
                                if (exception is CancellationException) throw exception
                                AppLog.w("PlaybackSessions", "Could not query ${session.device.friendlyName}: ${exception.message}")
                            }
                        }
                    }
                }
                delay(delay)
            }
        }
}
