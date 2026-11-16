package io.github.scovillo.playondlna.dlna.soap

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.transport.PlaybackCommand
import io.github.scovillo.playondlna.dlna.transport.PlaybackPositionInfo
import io.github.scovillo.playondlna.dlna.transport.TransportState
import io.github.scovillo.playondlna.dlna.transport.soap.SoapClient
import io.github.scovillo.playondlna.dlna.transport.soap.SoapCommand
import io.github.scovillo.playondlna.dlna.transport.soap.SoapDlnaTransport
import io.github.scovillo.playondlna.dlna.transport.testDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoapDlnaTransportTest {
    private val device = testDevice("renderer")
    private val commands = mutableListOf<SoapCommand>()
    private var response = ""
    private var failure: RuntimeException? = null
    private val transport =
        SoapDlnaTransport(
            object : SoapClient {
                override fun execute(command: SoapCommand): String {
                    commands += command
                    failure?.let { throw it }
                    return response
                }
            },
        )

    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun playSetsResourceUriAndMetadataBeforeStartingPlayback() =
        runBlocking {
            transport.play(device, "http://phone/id/audio.mp3", "<DIDL-Lite>metadata</DIDL-Lite>")
            assertEquals(listOf("SetAVTransportURI", "Play"), commands.map { it.actionName })
            assertTrue(commands[0].toPayload().contains("http://phone/id/audio.mp3"))
            assertTrue(commands[0].toPayload().contains("<DIDL-Lite>metadata</DIDL-Lite>"))
        }

    @Test
    fun commandsMapToTheirSoapActions() =
        runBlocking {
            PlaybackCommand.entries.forEach { transport.command(device, it) }
            assertEquals(listOf("Play", "Pause", "Stop", "Next", "Previous"), commands.map { it.actionName })
        }

    @Test
    fun positionFieldsComeFromOneGetPositionInfoResponse() =
        runBlocking {
            response = "<Track>3</Track><TrackURI>http://host/id/audio.mp3?a=1&amp;b=2</TrackURI><RelTime>00:01:12.5</RelTime><TrackDuration>00:04:40</TrackDuration>"
            assertEquals(PlaybackPositionInfo("http://host/id/audio.mp3?a=1&b=2", 72.5, 3, 280.0), transport.positionInfo(device))
            assertEquals(listOf("GetPositionInfo"), commands.map { it.actionName })
        }

    @Test
    fun readsTransportStateAndFormatsSeekAsRelativeTime() =
        runBlocking {
            response = "<CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState>"
            assertEquals(TransportState.PAUSED_PLAYBACK, transport.transportState(device))
            transport.seekTo(device, 70.9)
            assertEquals(listOf("GetTransportInfo", "Seek"), commands.map { it.actionName })
            assertTrue(commands.last().toPayload().contains("00:01:10"))
        }

    @Test
    fun failedSetUriDoesNotSendPlayAndPropagatesError() =
        runBlocking {
            val expected = IllegalStateException("Renderer unreachable")
            failure = expected
            try {
                transport.play(device, "http://phone/id/video.mp4", "metadata")
                throw AssertionError("Expected renderer failure")
            } catch (error: IllegalStateException) {
                assertEquals(expected.message, error.message)
            }
            assertEquals(listOf("SetAVTransportURI"), commands.map { it.actionName })
        }
}
