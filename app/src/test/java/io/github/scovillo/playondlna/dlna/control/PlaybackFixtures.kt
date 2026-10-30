package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Playlist
import io.github.scovillo.playondlna.server.MediaFileResource
import java.io.File

internal fun testDevice(id: String = "test") = DlnaDevice(id, "MediaRenderer", "http://$id/device.xml", id, "Test", "Test", "MediaRenderer", "http://$id/control", null)

internal fun testItem(id: String) = LibraryItem(LibraryMetadata(id, id, "Test", 60, "720p", false), File("$id.mp4"), null, null)

internal fun testSession(id: String = "test") =
    PlaybackSession(
        testDevice(id),
        Playlist("queue", "Test queue", listOf("one", "two", "three").map(::testItem)),
        0,
        TransportState.PLAYING,
        PlaybackPositionInfo("http://media/one/video.mp4", 12.0, 1, 60.0),
    )

internal open class StubDlnaTransport : DlnaTransport {
    override fun playFile(
        device: DlnaDevice,
        resource: MediaFileResource,
    ): Unit = error("Unexpected Play")

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ): Unit = error("Unexpected command")

    override fun transportState(device: DlnaDevice) = TransportState.PLAYING

    override fun positionInfo(device: DlnaDevice) = PlaybackPositionInfo("http://media/one/video.mp4", 12.0, 1, 60.0)

    override fun currentTrackUri(device: DlnaDevice): String = error("Use positionInfo")

    override fun currentPositionSeconds(device: DlnaDevice): Double = error("Use positionInfo")

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ): Unit = error("Unexpected seek")
}
