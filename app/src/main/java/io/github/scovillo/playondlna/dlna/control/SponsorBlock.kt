package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.persistence.SponsorBlockCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class SponsorSegment(val start: Double, val end: Double)

class SponsorBlockSkipDetector(private val segments: List<SponsorSegment>) {
    private val skippedSegments = mutableSetOf<SponsorSegment>()

    fun nextSeekPosition(positionSeconds: Double): Double? {
        skippedSegments.removeAll { positionSeconds < it.start }
        val segment =
            segments.firstOrNull {
                positionSeconds >= it.start && positionSeconds < it.end && it !in skippedSegments
            } ?: return null
        skippedSegments += segment
        return segment.end
    }
}

class SponsorBlockObservation(
    private val playbackSessions: SharedFlow<PlaybackSession>,
    cache: SponsorBlockCache,
    private val transport: DlnaTransport = SoapDlnaTransport(),
) {
    private val client = SponsorBlockClient(cache)

    suspend fun observe() {
        val detectors = mutableMapOf<String, SponsorBlockSkipDetector>()
        playbackSessions.collect { session ->
            if (session.currentState != TransportState.PLAYING) {
                AppLog.d("SponsorBlockObservation", "Session of ${session.device.friendlyName} in state=${session.currentState}, skipping...")
                return@collect
            }
            val mediaId = session.currentItem?.metadata?.id
            if (mediaId == null) {
                AppLog.d("SponsorBlockObservation", "Session of ${session.device.friendlyName} has no current item, skipping...")
                return@collect
            }
            try {
                val detector = detectors.getOrPut(session.id) {
                    SponsorBlockSkipDetector(client.fetchSegments(mediaId))
                }
                currentCoroutineContext().ensureActive()
                val skipPosition = detector.nextSeekPosition(session.currentPosition)
                if (skipPosition != null) {
                    AppLog.i("SponsorBlockObservation", "Skipping sponsorblock segments for session of ${session.device.friendlyName}")
                    transport.seekTo(session.device, skipPosition)
                } else {
                    AppLog.d("SponsorBlockObservation", "No sponsorblock segments found for session of ${session.device.friendlyName}")
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w("SponsorBlock", "Could not monitor ${session.device.friendlyName}: ${error.message}")
            }
        }
    }
}

class SponsorBlockClient(
    private val cache: SponsorBlockCache,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun fetchSegments(videoId: String): List<SponsorSegment> {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return emptyList()
        cache.load(videoId)?.let { return it }
        val url =
            "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
                .addQueryParameter("videoID", videoId)
                .addQueryParameter("categories", "[\"sponsor\"]")
                .build()
        AppLog.i("SponsorBlock", "Fetching segments for $videoId")
        val body =
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w("SponsorBlock", "Segment request for $videoId returned HTTP ${response.code}")
                    if (response.code == 404) cache.save(videoId, emptyList())
                    return emptyList()
                }
                response.body?.string().orEmpty()
            }
        val segments = JSONArray(body)
        return List(segments.length()) { index ->
            val range = segments.getJSONObject(index).getJSONArray("segment")
            SponsorSegment(range.getDouble(0), range.getDouble(1))
        }.filter { it.start >= 0 && it.end > it.start }.also {
            cache.save(videoId, it)
            AppLog.i("SponsorBlock", "Fetched ${it.size} segments for $videoId")
        }
    }
}
