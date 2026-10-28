package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.soap.UpnpActionException
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.persistence.SponsorBlockCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    @Test
    fun observesSingleTrackAfterPlayTransitionError() =
        runBlocking {
            val item = videoFile("transition-track")
            val renderer = device("samsung")
            val failures = CopyOnWriteArrayList<Throwable>()
            val transport =
                object : DlnaTransport by RecordingTransport() {
                    override fun playFile(
                        device: DlnaDevice,
                        item: LibraryItem,
                    ) {
                        throw UpnpActionException("Play", 500, "<errorCode>701</errorCode>")
                    }

                    override fun transportState(device: DlnaDevice) = TransportState.PLAYING

                    override fun positionInfo(device: DlnaDevice) = PlaybackPositionInfo(item.url, 42.0, 1, 60.0)
                }
            val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val remote =
                DlnaRemoteControl(
                    scope = controlScope,
                    transport = transport,
                    onPlaybackFailure = { failures += it },
                    sponsorBlockClient = SponsorBlockClient(SponsorBlockCache(cacheDirectory)),
                    playbackObservationDelay = 1.milliseconds,
                )
            try {
                remote.playMedia(renderer, item)
                val status = withTimeout(2_000) { remote.playbackStatuses.first { renderer.usn in it }.getValue(renderer.usn) }
                assertEquals(TransportState.PLAYING, status.transportState)
                assertEquals(42.0, status.positionSeconds)
                assertEquals(item.url, status.trackUri)
                assertEquals(emptyList<Throwable>(), failures.toList())
            } finally {
                controlScope.cancel()
            }
        }

    @Test
    fun reportsSingleTrackThatNeverStartsAfterPlayTransitionError() =
        runBlocking {
            val failures = CopyOnWriteArrayList<Throwable>()
            val transport =
                object : DlnaTransport by RecordingTransport() {
                    override fun playFile(
                        device: DlnaDevice,
                        item: LibraryItem,
                    ) {
                        throw UpnpActionException("Play", 500, "<errorCode>701</errorCode>")
                    }
                }
            val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val remote =
                DlnaRemoteControl(
                    scope = controlScope,
                    transport = transport,
                    onPlaybackFailure = { failures += it },
                    sponsorBlockClient = SponsorBlockClient(SponsorBlockCache(cacheDirectory)),
                    playbackObservationDelay = 1.milliseconds,
                )
            try {
                remote.playMedia(device("stopped"), videoFile("never-starts"))
                withTimeout(2_000.milliseconds) {
                    while (failures.isEmpty()) delay(1.milliseconds)
                }
                assertEquals("Renderer did not enter an active playback state", failures.single().message)
            } finally {
                controlScope.cancel()
            }
        }

    @Test
    fun seeksOnlyDuringActivePlaybackWithKnownDuration() =
        runBlocking {
            for (state in listOf(TransportState.STOPPED, TransportState.TRANSITIONING, TransportState.PLAYING, TransportState.PAUSED_PLAYBACK)) {
                val seeks = CopyOnWriteArrayList<Double>()
                val renderer = device("seek-test")
                val item = videoFile("seek-track")
                val transport =
                    object : DlnaTransport by RecordingTransport() {
                        override fun transportState(device: DlnaDevice) = state

                        override fun positionInfo(device: DlnaDevice) = PlaybackPositionInfo(item.url, 0.0, 1, 60.0)

                        override fun seekTo(
                            device: DlnaDevice,
                            seconds: Double,
                        ) {
                            seeks += seconds
                        }
                    }
                val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val remote =
                    DlnaRemoteControl(
                        scope = controlScope,
                        transport = transport,
                        sponsorBlockClient = SponsorBlockClient(SponsorBlockCache(cacheDirectory)),
                        playbackObservationDelay = 1.milliseconds,
                    )
                try {
                    remote.playMedia(renderer, item)
                    withTimeout(2_000.milliseconds) { remote.playbackStatuses.first { renderer.usn in it } }
                    remote.seekTo(renderer, 90.0)
                    if (state == TransportState.PLAYING || state == TransportState.PAUSED_PLAYBACK) {
                        withTimeout(2_000.milliseconds) {
                            while (seeks.isEmpty()) delay(1.milliseconds)
                        }
                        assertEquals(listOf(60.0), seeks.toList())
                    } else {
                        delay(50.milliseconds)
                        assertEquals(emptyList<Double>(), seeks.toList())
                    }
                } finally {
                    controlScope.cancel()
                }
            }
        }

    @Test
    fun singleTrackStopsObservationBeforeFileEnd() = verifyStoppedPlayback(playlist = false, atFileEnd = false)

    @Test
    fun playlistStopsObservationWithoutAdvancingBeforeFileEnd() = verifyStoppedPlayback(playlist = true, atFileEnd = false)

    @Test
    fun playlistAdvancesOnlyAtFileEnd() = verifyStoppedPlayback(playlist = true, atFileEnd = true)

    @Test
    fun explicitStopAtFileEndDoesNotAdvancePlaylist() = verifyStoppedPlayback(playlist = true, atFileEnd = true, explicitStop = true)

    @Test
    fun explicitStopEndsSingleTrackObservation() = verifyStoppedPlayback(playlist = false, atFileEnd = true, explicitStop = true)

    private fun verifyStoppedPlayback(
        playlist: Boolean,
        atFileEnd: Boolean,
        explicitStop: Boolean = false,
    ) = runBlocking {
        val played = CopyOnWriteArrayList<String>()
        val polls = java.util.concurrent.atomic.AtomicInteger()
        val manualStop = java.util.concurrent.atomic.AtomicBoolean()
        var trackPolls = 0
        var current: LibraryItem? = null
        val renderer = device("stopping")
        val transport =
            object : DlnaTransport by RecordingTransport() {
                override fun playFile(
                    device: DlnaDevice,
                    item: LibraryItem,
                ) {
                    current = item
                    trackPolls = 0
                    played += item.metadata.id
                }

                override fun transportState(device: DlnaDevice): TransportState {
                    polls.incrementAndGet()
                    return if (explicitStop) {
                        if (manualStop.get()) TransportState.STOPPED else TransportState.PLAYING
                    } else {
                        if (++trackPolls <= 3) TransportState.PLAYING else TransportState.STOPPED
                    }
                }

                override fun command(
                    device: DlnaDevice,
                    command: PlaybackCommand,
                ) {
                    if (command == PlaybackCommand.STOP) manualStop.set(true)
                }

                override fun positionInfo(device: DlnaDevice) = PlaybackPositionInfo(current!!.url, if (atFileEnd) 60.0 else 20.0, 1, 60.0)
            }
        val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failures = CopyOnWriteArrayList<Throwable>()
        val remote =
            DlnaRemoteControl(
                scope = controlScope,
                transport = transport,
                onPlaybackFailure = { failures += it },
                sponsorBlockClient = SponsorBlockClient(SponsorBlockCache(cacheDirectory)),
                playbackObservationDelay = 1.milliseconds,
            )
        val expectedTracks = if (playlist && atFileEnd && !explicitStop) 2 else 1
        try {
            val items = listOf(videoFile("stop-first"), videoFile("stop-second"))
            if (playlist) remote.playPlaylist(renderer, items) else remote.playMedia(renderer, items.first())
            if (explicitStop) {
                withTimeout(2_000.milliseconds) {
                    remote.playbackStatuses.first { it[renderer.usn]?.transportState == TransportState.PLAYING }
                }
                remote.command(renderer, PlaybackCommand.STOP)
            }
            withTimeout(2_000.milliseconds) {
                remote.playbackStatuses.first {
                    played.size == expectedTracks &&
                        it[renderer.usn]?.trackUri == items[expectedTracks - 1].url &&
                        it[renderer.usn]?.transportState == TransportState.STOPPED
                }
            }
            delay(50.milliseconds)
            val stoppedPollCount = polls.get()
            delay(50.milliseconds)
            assertEquals(stoppedPollCount, polls.get())
            assertEquals(expectedTracks, played.size)
            assertEquals(emptyList<Throwable>(), failures.toList())
        } finally {
            controlScope.cancel()
        }
    }

    @Test
    fun switchingPlayerStopsExternalObservationAndReselectingRestartsIt() = verifyObservationSelection("external")

    @Test
    fun switchingPlayerStopsSingleFileObservationWithoutStoppingPlayback() = verifyObservationSelection("single")

    @Test
    fun backgroundPlaylistKeepsObservingAndAdvancesWhileAnotherPlayerIsSelected() = verifyObservationSelection("playlist")

    private fun verifyObservationSelection(mode: String) =
        runBlocking {
            val first = device("first-player")
            val second = device("second-player")
            val items = listOf(videoFile("background-first"), videoFile("background-second"))
            val polls = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
            val currentItems = java.util.concurrent.ConcurrentHashMap<String, LibraryItem>()
            val finishFirst = java.util.concurrent.atomic.AtomicBoolean()
            val commands = CopyOnWriteArrayList<PlaybackCommand>()
            val played = CopyOnWriteArrayList<String>()
            val transport =
                object : DlnaTransport by RecordingTransport() {
                    override fun playFile(
                        device: DlnaDevice,
                        item: LibraryItem,
                    ) {
                        currentItems[device.usn] = item
                        played += item.metadata.id
                    }

                    override fun command(
                        device: DlnaDevice,
                        command: PlaybackCommand,
                    ) {
                        commands += command
                    }

                    override fun transportState(device: DlnaDevice): TransportState {
                        polls.computeIfAbsent(device.usn) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
                        return if (finishFirst.get() && currentItems[device.usn] == items.first()) TransportState.STOPPED else TransportState.PLAYING
                    }

                    override fun positionInfo(device: DlnaDevice) =
                        PlaybackPositionInfo(currentItems[device.usn]?.url ?: "http://external/media", if (finishFirst.get()) 60.0 else 10.0, 1, 60.0)
                }
            val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val failures = CopyOnWriteArrayList<Throwable>()
            val remote =
                DlnaRemoteControl(
                    scope = controlScope,
                    transport = transport,
                    onPlaybackFailure = { failures += it },
                    sponsorBlockClient = SponsorBlockClient(SponsorBlockCache(cacheDirectory)),
                    playbackObservationDelay = 1.milliseconds,
                )

            suspend fun awaitPolls(
                device: DlnaDevice,
                minimum: Int,
            ) {
                withTimeout(2_000.milliseconds) {
                    while ((polls[device.usn]?.get() ?: 0) < minimum) delay(1.milliseconds)
                }
            }
            try {
                when (mode) {
                    "playlist" -> remote.playPlaylist(first, items)
                    "single" -> remote.playMedia(first, items.first())
                    else -> remote.observeDevice(first)
                }
                awaitPolls(first, 3)
                remote.observeDevice(second)
                awaitPolls(second, 3)
                if (mode == "playlist") {
                    finishFirst.set(true)
                    withTimeout(2_000.milliseconds) {
                        while (items.last().metadata.id !in played) delay(1.milliseconds)
                    }
                    withTimeout(2_000.milliseconds) {
                        remote.playbackStatuses.first { it[first.usn]?.trackUri == items.last().url }
                    }
                    val before = polls.getValue(first.usn).get()
                    awaitPolls(first, before + 3)
                    assertEquals(listOf(items.first().metadata.id, items.last().metadata.id), played.toList())
                } else {
                    delay(50.milliseconds)
                    val stoppedCount = polls.getValue(first.usn).get()
                    delay(50.milliseconds)
                    assertEquals(stoppedCount, polls.getValue(first.usn).get())
                    remote.observeDevice(first)
                    awaitPolls(first, stoppedCount + 2)
                    assertEquals(if (mode == "single") listOf(items.first().metadata.id) else emptyList<String>(), played.toList())
                }
                assertEquals(emptyList<PlaybackCommand>(), commands.toList())
                assertEquals(emptyList<Throwable>(), failures.toList())
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
    private var expectedSeekCount = 0

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        val sponsorPosition = if (item == items[0]) 12.0 else 32.0
        statuses =
            buildList {
                repeat(3) { add(Status(TransportState.PLAYING, item.url, sponsorPosition)) }
                add(Status(TransportState.STOPPED, item.url, 60.0))
            }
        statusIndex = 0
        expectedSeekCount = seekPositions.size + 1
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
        if (seekPositions.size >= expectedSeekCount && statusIndex < statuses.lastIndex) {
            statusIndex++
        }
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
