package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.dlna.soap.UpnpActionException
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class DlnaRemoteControlSessionTest {
    @Before
    fun useConsoleLogger() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun keepsAppManagedPlaylistSessionsIndependentPerRenderer() =
        runBlocking {
            val transport = RecordingTransport()
            val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val remote = DlnaRemoteControl(controlScope, transport)
            val livingRoom = device("living-room")
            val kitchen = device("kitchen")

            try {
                remote.playPlaylist(livingRoom, nativePlaylist("living-room"), listOf(audioFile("living-room-track")))
                transport.awaitTrackCount(1)

                remote.playPlaylist(kitchen, nativePlaylist("kitchen"), listOf(audioFile("kitchen-track")))
                transport.awaitTrackCount(2)

                remote.command(livingRoom, PlaybackCommand.NEXT)
                delay(250)

                assertFalse(transport.commands.contains(livingRoom.location to PlaybackCommand.NEXT))
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

    private fun nativePlaylist(id: String) = DlnaPlaylist(Playlist(id, id, emptyList()), emptyList(), "http://server")

    private fun audioFile(id: String) =
        LibraryItem(
            LibraryMetadata(
                id = id,
                title = id,
                uploader = "Uploader",
                durationInSeconds = 10,
                isAudioOnly = true,
                qualityName = "Audio"
            ),
            mediaFile = File("$id.mp3"),
            thumbnail = null,
            subtitle = null,
        )
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

    override fun playPlaylist(
        device: DlnaDevice,
        playlist: DlnaPlaylist,
    ) {
        throw UpnpActionException("SetAVTransportURI", 500, "<errorCode>714</errorCode>")
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        commands += device.location to command
    }

    override fun transportState(device: DlnaDevice): TransportState = TransportState.STOPPED

    override fun currentTrackUri(device: DlnaDevice): String? = null

    suspend fun awaitTrackCount(count: Int) {
        withTimeout(2_000) {
            while (playedTracks.size < count) delay(10)
        }
    }
}
