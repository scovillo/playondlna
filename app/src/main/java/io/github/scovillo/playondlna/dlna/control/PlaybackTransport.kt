package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaMedia
import io.github.scovillo.playondlna.dlna.TransportState

/**
 * Abstraction for playback-related transport operations.
 * Can be replaced with mock implementations for testing.
 * Used by playback strategies and controllers.
 */
interface PlaybackTransport {
    /**
     * Play media at the given URI with metadata.
     */
    fun play(media: DlnaMedia)

    /**
     * Send a playback command (pause, stop, next, previous, etc).
     */
    fun command(command: PlaybackCommand)

    /**
     * Query current transport state.
     */
    fun transportState(): TransportState

    /**
     * Get the URI of the currently playing track.
     */
    fun currentTrackUri(): String?

    /**
     * Seek to a specific track (track-based seeking).
     */
    fun seekToTrack(trackNumber: Int)
}
