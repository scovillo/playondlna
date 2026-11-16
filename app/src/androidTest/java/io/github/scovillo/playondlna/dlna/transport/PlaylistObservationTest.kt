package io.github.scovillo.playondlna.dlna.transport

import fi.iki.elonen.NanoHTTPD
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.observation.PlaybackSession
import io.github.scovillo.playondlna.dlna.observation.PlaylistObservation
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.server.LocalIpAddress
import io.github.scovillo.playondlna.server.ServerPort
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class PlaylistObservationTest {
    private data class Request(val player: String, val action: String, val body: String)

    private val requests = CopyOnWriteArrayList<Request>()
    private val played = Channel<Request>(Channel.UNLIMITED)
    private val renderer =
        object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                val request = Request(session.uri, session.headers["soapaction"].orEmpty().substringAfterLast('#').trim('"'), files["postData"].orEmpty())
                requests += request
                if (request.action == "Play") played.trySend(request)
                return newFixedLengthResponse(Response.Status.OK, "text/xml", "<Envelope><Body/></Envelope>")
            }
        }

    @Before
    fun startRenderer() {
        renderer.start()
    }

    @After
    fun stopRenderer() {
        renderer.stop()
        played.close()
    }

    @Test
    fun waitsForStoppedAndAdvancesOnlyOnceWhenRendererResetsPosition() =
        runBlocking {
            val updates = MutableSharedFlow<PlaybackSession>()
            val observation = PlaylistObservation(updates, LocalIpAddress(), ServerPort())
            val job = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { observation.observe() }
            val session = session("one")
            try {
                updates.emit(session)
                assertTrue(requests.isEmpty())
                val stopped = session.copy(currentState = TransportState.STOPPED, currentIndex = -1, positionInfo = PlaybackPositionInfo())
                updates.emit(stopped)
                withTimeout(5_000) { played.receive() }
                updates.emit(stopped)
                updates.emit(session("barrier").copy(currentState = TransportState.PAUSED_PLAYBACK))
                assertEquals(listOf("SetAVTransportURI", "Play"), requests.map { it.action })
                assertTrue(requests.first().body.contains("/two/audio.mp3"))
            } finally {
                job.cancelAndJoin()
            }
        }

    @Test
    fun earlyStopAndUnknownDurationDoNotAdvance() =
        runBlocking {
            val updates = MutableSharedFlow<PlaybackSession>()
            val observation = PlaylistObservation(updates, LocalIpAddress(), ServerPort())
            val job = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { observation.observe() }
            try {
                val session = session("one")
                updates.emit(session.copy(positionInfo = PlaybackPositionInfo("", 20.0, 1, 100.0)))
                updates.emit(session.copy(currentState = TransportState.STOPPED))
                updates.emit(session.copy(positionInfo = PlaybackPositionInfo("", 99.0, 1, 0.0)))
                updates.emit(session.copy(currentState = TransportState.STOPPED))
                updates.emit(session.copy(currentState = TransportState.PAUSED_PLAYBACK))
                assertTrue(requests.isEmpty())
            } finally {
                job.cancelAndJoin()
            }
        }

    @Test
    fun endMarkersAreIndependentPerPlayer() =
        runBlocking {
            val updates = MutableSharedFlow<PlaybackSession>()
            val observation = PlaylistObservation(updates, LocalIpAddress(), ServerPort())
            val job = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { observation.observe() }
            try {
                val first = session("first")
                val second = session("second")
                updates.emit(first)
                updates.emit(second.copy(currentState = TransportState.STOPPED))
                updates.emit(first.copy(currentState = TransportState.STOPPED))
                assertEquals("/first", withTimeout(5_000) { played.receive() }.player)
                updates.emit(second)
                updates.emit(second.copy(currentState = TransportState.STOPPED))
                assertEquals("/second", withTimeout(5_000) { played.receive() }.player)
                assertEquals(2, requests.count { it.action == "Play" })
            } finally {
                job.cancelAndJoin()
            }
        }

    private fun session(id: String): PlaybackSession {
        val device = DlnaDevice(id, "MediaRenderer", "http://test/device.xml", id, "Test", "Test", "MediaRenderer", "http://127.0.0.1:${renderer.listeningPort}/$id", null)
        val items =
            listOf("one", "two").map {
                LibraryItem(LibraryMetadata(it, it, "Uploader", 100, "Audio", true), File("$it.mp3"), null, null)
            }
        return PlaybackSession(
            device,
            Playlist("queue", "Queue", items),
            0,
            TransportState.PLAYING,
            PlaybackPositionInfo("", 99.0, 1, 100.0)
        )
    }
}
