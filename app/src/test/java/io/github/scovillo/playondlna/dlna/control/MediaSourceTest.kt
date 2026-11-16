package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.persistence.SponsorBlockCache
import io.github.scovillo.playondlna.server.MediaFileResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaSourceTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun preservesSourceInStoredMetadata() {
        val metadata = metadata("abcdefghijk", "YouTube")
        assertEquals(metadata, LibraryMetadata.fromJson(metadata.toJson()))
    }

    @Test
    fun readsOldMetadataWithUnknownSource() {
        val json = JSONObject(metadata("abcdefghijk", "YouTube").toJson())
        json.remove("source")
        assertEquals("", LibraryMetadata.fromJson(json.toString()).source)
    }

    @Test
    fun supportsYouTubeAndUnknownSourcesButSkipsKnownOtherSources() =
        runBlocking {
            val directory = Files.createTempDirectory("sponsor-source-test").toFile()
            val cache = SponsorBlockCache(directory)
            val updates = MutableSharedFlow<PlaybackSession>()
            val seeks = mutableListOf<Double>()
            val skipped = CompletableDeferred<Unit>()
            val transport =
                object : DlnaTransport {
                    override suspend fun playFile(
                        device: DlnaDevice,
                        resource: MediaFileResource,
                    ) = Unit

                    override suspend fun command(
                        device: DlnaDevice,
                        command: PlaybackCommand,
                    ) = Unit

                    override suspend fun transportState(device: DlnaDevice) = TransportState.PLAYING

                    override suspend fun currentTrackUri(device: DlnaDevice) = ""

                    override suspend fun currentPositionSeconds(device: DlnaDevice) = 12.0

                    override suspend fun seekTo(
                        device: DlnaDevice,
                        seconds: Double,
                    ) {
                        seeks.add(seconds)
                        if (device.usn == "abcdefghij2") skipped.complete(Unit)
                    }
                }
            val observation = SponsorBlockObservation(updates, cache, transport)
            val job = launch(start = CoroutineStart.UNDISPATCHED) { observation.observe() }
            try {
                listOf("", "SoundCloud", "yOuTuBe").forEachIndexed { index, source ->
                    val id = "abcdefghij$index"
                    cache.save(id, listOf(SponsorSegment(10.0, 20.0)))
                    val item = LibraryItem(metadata(id, source), File("$id.mp4"), null, null)
                    val device = DlnaDevice(id, "MediaRenderer", "http://test/device.xml", "Test", "Test", "Test", "MediaRenderer", "http://test/control", null)
                    updates.emit(PlaybackSession(device, Playlist("test", "Test", listOf(item)), 0, TransportState.PLAYING, PlaybackPositionInfo("", 12.0)))
                }
                withTimeout(2_000) { skipped.await() }
                assertEquals(listOf(21.0, 21.0), seeks)
            } finally {
                job.cancelAndJoin()
                directory.deleteRecursively()
            }
        }

    private fun metadata(
        id: String,
        source: String,
    ) = LibraryMetadata(id, "Test", "Test", 60, "720p", false, source)
}
