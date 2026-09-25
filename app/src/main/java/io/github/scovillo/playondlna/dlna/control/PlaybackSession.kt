package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.model.LibraryItem

data class PlaybackSession(
    val device: DlnaDevice,
    val items: List<LibraryItem>,
    val currentIndex: Int,
    val unsupportedCommands: Set<PlaybackCommand> = emptySet(),
    val transportState: TransportState = TransportState.PLAYING,
) {
    val lastIndex: Int
        get() = items.lastIndex

    fun nextIndex(command: PlaybackCommand): Int =
        when (command) {
            PlaybackCommand.NEXT -> (currentIndex + 1).takeIf { it <= lastIndex } ?: currentIndex
            PlaybackCommand.PREVIOUS -> (currentIndex - 1).takeIf { it >= 0 } ?: currentIndex
            else -> currentIndex
        }
}
