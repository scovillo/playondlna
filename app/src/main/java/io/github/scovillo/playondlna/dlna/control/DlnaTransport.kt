package io.github.scovillo.playondlna.dlna.control

import io.github.scovillo.playondlna.dlna.DlnaDevice
import io.github.scovillo.playondlna.dlna.soap.SoapPlaybackTransportFactory
import io.github.scovillo.playondlna.model.LibraryItem

/** Transport boundary used by [io.github.scovillo.playondlna.ui.DlnaRemoteControl] - provides legacy compatibility layer */
interface DlnaTransport {
    fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    )

    fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    )

    fun transportState(device: DlnaDevice): TransportState

    fun currentTrackUri(device: DlnaDevice): String?

    fun currentPositionSeconds(device: DlnaDevice): Double?

    fun positionInfo(device: DlnaDevice): PlaybackPositionInfo = PlaybackPositionInfo(currentTrackUri(device), currentPositionSeconds(device))

    fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    )
}

/**
 * Adapter from DlnaTransport to PlaybackTransport.
 * Creates appropriate PlaybackTransport implementations for communication with a device.
 */
class SoapDlnaTransport : DlnaTransport {
    private val transportFactory = SoapPlaybackTransportFactory()

    private fun transportFor(device: DlnaDevice): PlaybackTransport = transportFactory.create(device.requireAvTransportUrl())

    override fun playFile(
        device: DlnaDevice,
        item: LibraryItem,
    ) {
        transportFor(device).play(item.url, item.metaDataDidlLite)
    }

    override fun command(
        device: DlnaDevice,
        command: PlaybackCommand,
    ) {
        transportFor(device).command(command)
    }

    override fun transportState(device: DlnaDevice): TransportState = transportFor(device).transportState()

    override fun currentTrackUri(device: DlnaDevice): String? = transportFor(device).currentTrackUri()

    override fun currentPositionSeconds(device: DlnaDevice): Double? = transportFor(device).currentPositionSeconds()

    override fun positionInfo(device: DlnaDevice): PlaybackPositionInfo = transportFor(device).positionInfo()

    override fun seekTo(
        device: DlnaDevice,
        seconds: Double,
    ) = transportFor(device).seekTo(seconds)
}
