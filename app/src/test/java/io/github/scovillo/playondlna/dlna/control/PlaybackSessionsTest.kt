package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.server.MediaFileResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class PlaybackSessionsTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun publishesRegistrationAndRemovalToNewSubscribers() =
        runBlocking {
            val sessions = PlaybackSessions()
            val session = session()
            sessions.register(session)
            assertEquals(session, sessions.sessions.first()[session.id])
            sessions.remove(session.id)
            assertTrue(sessions.sessions.first().isEmpty())
        }

    @Test
    fun concurrentUpdatesDoNotLoseChanges() =
        runBlocking {
            val sessions = PlaybackSessions()
            sessions.register(session())
            val jobs =
                List(8) {
                    launch(Dispatchers.Default) {
                        repeat(100) {
                            sessions.update("test") { it.copy(currentIndex = it.currentIndex + 1) }
                        }
                    }
                }
            jobs.forEach { it.join() }
            assertEquals(800, sessions.sessions.value.getValue("test").currentIndex)
        }

    @Test
    fun observationPreservesConcurrentEditsAndSessionsOnCancellation() =
        runBlocking {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val sessions = PlaybackSessions(transport(entered, release), 1.milliseconds)
            sessions.register(session())
            val job = launch { sessions.observe() }
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    assertTrue(entered.await(2, TimeUnit.SECONDS))
                }
                sessions.update("test") { it.copy(unsupportedCommands = setOf(PlaybackCommand.PAUSE)) }
                release.countDown()
                val updated =
                    withTimeout(2_000) {
                        sessions.sessions.first { it["test"]?.currentPosition == 12.0 }.getValue("test")
                    }
                assertEquals(TransportState.PAUSED_PLAYBACK, updated.currentState)
                assertEquals(setOf(PlaybackCommand.PAUSE), updated.unsupportedCommands)
            } finally {
                release.countDown()
                job.cancelAndJoin()
            }
            assertTrue(sessions.sessions.value.containsKey("test"))
        }

    @Test
    fun latePollCannotOverwriteReplacementSession() =
        runBlocking {
            val secondPoll = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val replacement = session()
            lateinit var sessions: PlaybackSessions
            val transport =
                object : DlnaTransport by transport(CountDownLatch(0), CountDownLatch(0)) {
                    override fun transportState(device: DlnaDevice): TransportState {
                        if (calls.incrementAndGet() == 2) {
                            secondPoll.countDown()
                            check(release.await(2, TimeUnit.SECONDS))
                        }
                        return TransportState.PAUSED_PLAYBACK
                    }

                    override fun positionInfo(device: DlnaDevice): PlaybackPositionInfo {
                        sessions.remove("test")
                        sessions.register(replacement)
                        return PlaybackPositionInfo("http://test/media", 12.0)
                    }
                }
            sessions = PlaybackSessions(transport, 1.milliseconds)
            sessions.register(session())
            val job = launch { sessions.observe() }
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    assertTrue(secondPoll.await(2, TimeUnit.SECONDS))
                }
                assertEquals(replacement, sessions.sessions.value["test"])
            } finally {
                job.cancel()
                release.countDown()
                job.join()
            }
        }

    private fun session() =
        PlaybackSession(
            device =
                DlnaDevice(
                    usn = "test",
                    st = "MediaRenderer",
                    location = "http://test/device.xml",
                    friendlyName = "test",
                    manufacturer = "Test",
                    modelName = "Test",
                    deviceType = "MediaRenderer",
                    avTransportUrl = "http://test/avtransport",
                    renderingControlUrl = null,
                ),
            queue = emptyList(),
            currentIndex = 0,
        )

    private fun transport(
        entered: CountDownLatch,
        release: CountDownLatch,
    ) = object : DlnaTransport {
        override fun playFile(
            device: DlnaDevice,
            resource: MediaFileResource,
        ) = Unit

        override fun command(
            device: DlnaDevice,
            command: PlaybackCommand,
        ) = Unit

        override fun transportState(device: DlnaDevice) = TransportState.PAUSED_PLAYBACK

        override fun currentTrackUri(device: DlnaDevice): String? = null

        override fun currentPositionSeconds(device: DlnaDevice): Double? = null

        override fun seekTo(
            device: DlnaDevice,
            seconds: Double,
        ) = Unit

        override fun positionInfo(device: DlnaDevice): PlaybackPositionInfo {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            return PlaybackPositionInfo("http://test/media", 12.0)
        }
    }
}
