package io.github.scovillo.playondlna.dlna.transport

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.observation.PlaybackSessions
import io.github.scovillo.playondlna.model.Playlist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class PlaybackSessionsTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun registeringExistingPlayerPreservesItsSession() {
        val sessions = PlaybackSessions()
        val original = testSession()
        sessions.register(original)
        sessions.register(original.copy(playlist = Playlist.empty()))
        assertSame(original, sessions.get(original.id))
        sessions.remove(original.id)
        assertFalse(sessions.has(original.id))
    }

    @Test
    fun replacingPlaylistResetsIndexAndLeavesOtherPlayersUntouched() {
        val sessions = PlaybackSessions()
        sessions.register(testSession().copy(currentIndex = 2))
        val other = testSession("other")
        sessions.register(other)
        val queue = Playlist("new", "New", listOf(testItem("new")))
        sessions.update("test", queue)
        assertSame(queue, sessions.get("test").playlist)
        assertEquals(0, sessions.get("test").currentIndex)
        assertSame(other, sessions.get("other"))
        sessions.update("missing", queue)
        assertFalse(sessions.has("missing"))
    }

    @Test
    fun pollPublishesMeasuredStateWithoutLosingPlaylistChangedDuringRequest() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val transport =
                object : StubDlnaTransport() {
                    override suspend fun playbackInfo(device: DlnaDevice): PlaybackInfo {
                        entered.complete(Unit)
                        release.await()
                        return PlaybackInfo(TransportState.PAUSED_PLAYBACK, PlaybackPositionInfo("http://media/new/video.mp4", 18.0))
                    }
                }
            val sessions = PlaybackSessions(transport, 1.milliseconds)
            sessions.register(testSession())
            val update = async(start = CoroutineStart.UNDISPATCHED) { sessions.updates.first() }
            val job = launch { sessions.observe() }
            try {
                withTimeout(2_000) { entered.await() }
                val queue = Playlist("new", "New", listOf(testItem("new")))
                sessions.update("test", queue)
                release.complete(Unit)
                val result = withTimeout(2_000) { update.await() }
                assertSame(queue, result.playlist)
                assertEquals(0, result.currentIndex)
                assertEquals(18.0, result.currentPosition, 0.0)
                assertEquals(TransportState.PAUSED_PLAYBACK, result.currentState)
            } finally {
                job.cancelAndJoin()
                update.cancelAndJoin()
            }
            assertEquals("new", sessions.get("test").playlist.id)
        }

    @Test
    fun transientFailureIsRetriedWithoutBlockingOtherPlayers() =
        runBlocking {
            val attempts = AtomicInteger()
            val transport =
                object : StubDlnaTransport() {
                    override suspend fun playbackInfo(device: DlnaDevice): PlaybackInfo {
                        if (device.usn == "test" && attempts.incrementAndGet() == 1) error("Temporary network failure")
                        return PlaybackInfo(TransportState.PLAYING, positionInfo(device))
                    }
                }
            val sessions = PlaybackSessions(transport, 1.milliseconds)
            sessions.register(testSession())
            sessions.register(testSession("other"))
            val first = async(start = CoroutineStart.UNDISPATCHED) { sessions.updates.first { it.id == "test" } }
            val other = async(start = CoroutineStart.UNDISPATCHED) { sessions.updates.first { it.id == "other" } }
            val job = launch { sessions.observe() }
            try {
                withTimeout(2_000) {
                    assertEquals("other", other.await().id)
                    assertEquals("test", first.await().id)
                }
            } finally {
                job.cancelAndJoin()
                first.cancelAndJoin()
                other.cancelAndJoin()
            }
        }

    @Test
    fun removingSessionDuringRequestDoesNotReinsertIt() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val nextRound = CompletableDeferred<Unit>()
            val transport =
                object : StubDlnaTransport() {
                    override suspend fun playbackInfo(device: DlnaDevice): PlaybackInfo {
                        if (device.usn == "test") {
                            entered.complete(Unit)
                            release.await()
                        } else if (release.isCompleted) {
                            nextRound.complete(Unit)
                        }
                        return PlaybackInfo(TransportState.PLAYING, positionInfo(device))
                    }
                }
            val sessions = PlaybackSessions(transport, 1.milliseconds)
            sessions.register(testSession())
            sessions.register(testSession("other"))
            val job = launch { sessions.observe() }
            try {
                withTimeout(2_000) { entered.await() }
                sessions.remove("test")
                release.complete(Unit)
                withTimeout(2_000) { nextRound.await() }
                assertFalse(sessions.has("test"))
            } finally {
                job.cancelAndJoin()
            }
        }
}
