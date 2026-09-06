package io.github.scovillo.playondlna.dlna.control

/**
 * Abstraction for playback-related transport operations.
 * Can be replaced with mock implementations for testing.
 * Used by playback strategies and controllers.
 */
interface PlaybackTransport {
    /**
     * Set the transport URI and its DIDL-Lite metadata, then start playback.
     */
    fun play(
        url: String,
        metadata: String,
    )

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

    fun currentPositionSeconds(): Double?

    /** Read URI and position from one GetPositionInfo request where possible. */
    fun positionInfo(): PlaybackPositionInfo =
        PlaybackPositionInfo(
            trackUri = currentTrackUri(),
            positionSeconds = currentPositionSeconds(),
            durationSeconds = null,
        )

    fun mediaInfo(): PlaybackMediaInfo? = null

    fun seekTo(seconds: Double)
}

data class PlaybackPositionInfo(
    val trackUri: String?,
    val positionSeconds: Double?,
    val trackNumber: Int? = null,
    val durationSeconds: Double? = null,
)

data class PlaybackMediaInfo(
    val numberOfTracks: Int?,
    val currentUri: String?,
    val nextUri: String?,
)
