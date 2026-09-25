package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.persistence.SponsorBlockCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

class DlnaRemoteControlSessionTest {
    private lateinit var cacheDirectory: File

    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
        cacheDirectory = Files.createTempDirectory("dlna-remote-control-test").toFile()
    }

    @After
    fun deleteCacheDirectory() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun keepsAppManagedPlaylistSessionsIndependentPerRenderer() =
        runBlocking {
            val transport = RecordingTransport()
            val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val remote =
                DlnaRemoteControl(
                    scope = controlScope,
                    transport = transport,
                    sponsorBlockClient = SponsorBlockClient(SponsorBlockCache(cacheDirectory)),
                )
            val livingRoom = device("living-room")
            val kitchen = device("kitchen")

            try {
                remote.playPlaylist(livingRoom, listOf(audioFile("living-room-track")))
                transport.awaitTrackCount(1)

                remote.playPlaylist(kitchen, listOf(audioFile("kitchen-track")))
                transport.awaitTrackCount(2)

                remote.command(livingRoom, PlaybackCommand.NEXT)
                delay(250.milliseconds)

                assertFalse(transport.commands.contains(livingRoom.location to PlaybackCommand.NEXT))
            } finally {
                controlScope.cancel()
            }
        }

    @Test
    fun skipsSponsorSegmentsAcrossAppManagedPlaylistTracks() =
        runBlocking {
            val items = listOf(videoFile("ccccccccccc"), videoFile("ddddddddddd"))
            val cache = SponsorBlockCache(cacheDirectory)
            cache.save(items[0].metadata.id, listOf(SponsorSegment(10.0, 20.0)))
            cache.save(items[1].metadata.id, listOf(SponsorSegment(30.0, 40.0)))
            val transport = SponsorBlockPlaylistTransport(items)
            val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val remote =
                DlnaRemoteControl(
                    scope = controlScope,
                    transport = transport,
                    isSponsorBlockEnabled = { true },
                    sponsorBlockClient = SponsorBlockClient(cache),
                    playbackObservationDelay = 1.milliseconds,
                )

            try {
                remote.playPlaylist(device("managed"), items)
                transport.awaitSeekCount(2)

                assertEquals(listOf(20.0, 40.0), transport.seekPositions)
            } finally {
                controlScope.cancel()
            }
        }

    private fun device(id: String) =
        DlnaDevice(
            usn = id,
            st = "MediaRenderer",
            location = "http://$id/device.xml",
            friendlyName = id,
            manufacturer = "Test",
            modelName = "Test",
            deviceType = "MediaRenderer",
            avTransportUrl = "http://$id/avtransport",
            renderingControlUrl = null,
        )

    private fun audioFile(id: String) =
        LibraryItem(
            LibraryMetadata(
                id = id,
                title = id,
                uploader = "Uploader",
                durationInSeconds = 10,
                isAudioOnly = true,
                qualityName = "Audio",
            ),
            mediaFile = File("$id.mp3"),
            thumbnail = null,
            subtitle = null,
        )

    private fun videoFile(id: String) =
        LibraryItem(
            LibraryMetadata(
                id = id,
                title = id,
                uploader = "Uploader",
                durationInSeconds = 60,
                isAudioOnly = false,
                qualityName = "720p",
            ),
            mediaFile = File("$id.mp4"),
            thumbnail = null,
            subtitle = null,
        )
}

private class SponsorBlockPlaylistTransport(
    private val items: List<LibraryItem>,
) : DlnaTransport {
    private data class Status(
        val state: TransportState,
        val uri: String,
        val position: Double,
        val trackNumber: Int? = null,
    )

    val seekPositions = CopyOnWriteArrayList<Double>()
    private var statuses = emptyList<Status>()
    private var statusIndex = 0

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        val sponsorPosition = if (item == items[0]) 12.0 else 32.0
        statuses =
            buildList {
                repeat(3) { add(Status(TransportState.PLAYING, item.url, sponsorPosition)) }
                add(Status(TransportState.STOPPED, item.url, sponsorPosition + 10.0))
            }
        statusIndex = 0
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) = Unit

    override fun transportState(device: DlnaDevice): TransportState = currentStatus().state

    override fun currentTrackUri(device: DlnaDevice): String = currentStatus().uri

    override fun currentPositionSeconds(device: DlnaDevice): Double = currentStatus().position

    override fun positionInfo(device: DlnaDevice): PlaybackPositionInfo {
        val status = currentStatus()
        if (statusIndex < statuses.lastIndex) statusIndex++
        return PlaybackPositionInfo(status.uri, status.position, status.trackNumber)
    }

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) {
        seekPositions += seconds
    }

    private fun currentStatus(): Status = statuses[statusIndex]

    suspend fun awaitSeekCount(count: Int) {
        withTimeout(2_000.milliseconds) {
            while (seekPositions.size < count) delay(1.milliseconds)
        }
    }
}

private class RecordingTransport : DlnaTransport {
    val commands = CopyOnWriteArrayList<Pair<String, PlaybackCommand>>()
    private val playedTracks = CopyOnWriteArrayList<Pair<String, String>>()

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        playedTracks += device.location to item.metadata.id
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        commands += device.location to command
    }

    override fun transportState(device: DlnaDevice): TransportState = TransportState.STOPPED

    override fun currentTrackUri(device: DlnaDevice): String? = null

    override fun currentPositionSeconds(device: DlnaDevice): Double? = null

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) = Unit

    suspend fun awaitTrackCount(count: Int) {
        withTimeout(2_000.milliseconds) {
            while (playedTracks.size < count) delay(10.milliseconds)
        }
    }
}
