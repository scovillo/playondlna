package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.server.MediaFileResource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DlnaPlaybackInfoTest {
    @Test
    fun readsStateAndPositionConcurrentlyOnce() =
        runBlocking {
            val started = CountDownLatch(2)
            val stateCalls = AtomicInteger()
            val positionCalls = AtomicInteger()
            val position = PlaybackPositionInfo("http://test/video.mp4", 12.0, 1, 60.0)
            val transport =
                object : DlnaTransport {
                    override fun playFile(
                        device: DlnaDevice,
                        resource: MediaFileResource,
                    ) = Unit

                    override fun command(
                        device: DlnaDevice,
                        command: PlaybackCommand,
                    ) = Unit

                    override fun currentTrackUri(device: DlnaDevice): String = error("Use the combined position response")

                    override fun currentPositionSeconds(device: DlnaDevice): Double = error("Use the combined position response")

                    override fun seekTo(
                        device: DlnaDevice,
                        seconds: Double,
                    ) = Unit

                    override fun transportState(device: DlnaDevice): TransportState {
                        stateCalls.incrementAndGet()
                        started.countDown()
                        assertTrue(started.await(2, TimeUnit.SECONDS))
                        return TransportState.PLAYING
                    }

                    override fun positionInfo(device: DlnaDevice): PlaybackPositionInfo {
                        positionCalls.incrementAndGet()
                        started.countDown()
                        assertTrue(started.await(2, TimeUnit.SECONDS))
                        return position
                    }
                }
            val device =
                DlnaDevice(
                    usn = "test",
                    st = "MediaRenderer",
                    location = "http://test/device.xml",
                    friendlyName = "Test",
                    manufacturer = "Test",
                    modelName = "Test",
                    deviceType = "MediaRenderer",
                    avTransportUrl = "http://test/avtransport",
                    renderingControlUrl = null,
                )

            assertEquals(PlaybackInfo(TransportState.PLAYING, position), transport.playbackInfo(device))
            assertEquals(1, stateCalls.get())
            assertEquals(1, positionCalls.get())
        }
}
