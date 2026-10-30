package io.github.scovillo.playondlna.dlna.control

/**
 * Abstraction for playback-related transport operations.
 * Can be replaced copy mock implementations for testing.
 * Used by playback strategies and controllers.
 */
interface PlaybackTransport {
    /**
     * Set the transport URI and its DIDL-Lite metadata, then observe playback.
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
    fun currentTrackUri(): String

    fun currentPositionSeconds(): Double

    /** Read URI and position from one GetPositionInfo request where possible. */
    fun positionInfo(): PlaybackPositionInfo =
        PlaybackPositionInfo(
            trackUri = currentTrackUri(),
            positionSeconds = currentPositionSeconds(),
            durationSeconds = 0.0,
        )

    fun mediaInfo(): PlaybackMediaInfo? = null

    fun seekTo(seconds: Double)
}

data class PlaybackPositionInfo(
    val trackUri: String = "",
    val positionSeconds: Double = 0.0,
    val trackNumber: Int = 0,
    val durationSeconds: Double = 0.0,
)

data class PlaybackMediaInfo(
    val numberOfTracks: Int?,
    val currentUri: String?,
    val nextUri: String?,
)

/** Combined result of GetTransportInfo and GetPositionInfo. */
data class PlaybackInfo(
    val state: TransportState,
    val positionInfo: PlaybackPositionInfo,
)
