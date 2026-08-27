package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.TransportState
import java.net.URI

/**
 * Detects when playback of a track has ended on the renderer.
 * Polls transport state and current track URI to determine end of playback.
 */
class PlaybackEndDetector {
    private var expectedTrackObserved = false
    private var consecutiveActivePolls = 0
    private var playbackObserved = false

    /**
     * Observe a single polling cycle.
     *
     * @return true if playback has ended, false otherwise
     */
    fun observe(
        transportState: TransportState,
        currentTrackUri: String?,
        expectedTrackUri: String,
    ): Boolean {
        expectedTrackObserved = expectedTrackObserved || trackUrisMatch(currentTrackUri, expectedTrackUri)

        val isActive =
            transportState in
                setOf(TransportState.PLAYING, TransportState.TRANSITIONING, TransportState.PAUSED_PLAYBACK)
        consecutiveActivePolls = if (isActive) consecutiveActivePolls + 1 else 0

        // Require the expected URI to have been seen in an earlier poll. Transport
        // state and position are separate SOAP calls and can straddle a transition.
        // After three stable active polls, fall back to transport state because some
        // renderers omit or rewrite TrackURI even though playback works.
        val stateBelongsToExpectedTrack = expectedTrackObserved || consecutiveActivePolls >= 3
        playbackObserved = playbackObserved || (stateBelongsToExpectedTrack && isActive)

        val playbackEnded =
            playbackObserved &&
                transportState in setOf(TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT)

        AppLog.i("PlaybackEndDetector", "State: $transportState, URI: $currentTrackUri, Ended: $playbackEnded")

        return playbackEnded
    }

    /**
     * Check if we have observed active playback yet.
     * Used to detect if renderer is responding and playing.
     */
    fun hasObservedPlayback(): Boolean = playbackObserved

    private fun trackUrisMatch(
        currentTrackUri: String?,
        expectedTrackUri: String,
    ): Boolean {
        if (currentTrackUri == null) return false
        if (currentTrackUri == expectedTrackUri) return true
        return runCatching {
            val currentPath = URI(currentTrackUri).normalize().path
            val expectedPath = URI(expectedTrackUri).normalize().path
            currentPath.isNotEmpty() && currentPath == expectedPath
        }.getOrDefault(false)
    }
}
