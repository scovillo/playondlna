package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.model.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class PlaybackObservationTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun canBeStoppedAndRestarted() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val transport = GenerationTransport()
            val observation = PlaybackObservation(scope, device(), transport, 1.milliseconds)
            try {
                observation.start()
                assertEquals(1.0, withTimeout(1_000.milliseconds) { observation.status.first().positionSeconds })

                observation.stop()
                transport.generation = 2.0
                observation.start()
                assertEquals(
                    2.0,
                    withTimeout(1_000.milliseconds) {
                        observation.status.first { it.positionSeconds == 2.0 }.positionSeconds
                    },
                )
            } finally {
                observation.stop()
                scope.cancel()
            }
        }

    @Test
    fun keepsPollingAcrossTemporaryStoppedState() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val observation = PlaybackObservation(scope, device(), SequenceTransport(), 1.milliseconds)
            try {
                observation.start()
                withTimeout(1_000.milliseconds) { observation.status.first { it.positionSeconds == 2.0 } }
                assertEquals(2.0, observation.status.first().positionSeconds)
            } finally {
                observation.stop()
                scope.cancel()
            }
        }

    private fun device() =
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
        )
}

private class GenerationTransport : DlnaTransport {
    @Volatile
    var generation = 1.0

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) = Unit

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) = Unit

    override fun transportState(device: DlnaDevice): TransportState = TransportState.PLAYING

    override fun currentTrackUri(device: DlnaDevice): String = "http://test/media"

    override fun currentPositionSeconds(device: DlnaDevice): Double = generation

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) = Unit
}

private class SequenceTransport : DlnaTransport {
    private var calls = 0

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) = Unit

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) = Unit

    override fun transportState(device: DlnaDevice): TransportState =
        when (calls++) {
            0 -> TransportState.PLAYING
            1 -> TransportState.STOPPED
            else -> TransportState.PLAYING
        }

    override fun currentTrackUri(device: DlnaDevice): String = "http://test/media"

    override fun currentPositionSeconds(device: DlnaDevice): Double? = if (calls <= 2) 1.0 else 2.0

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) = Unit
}
