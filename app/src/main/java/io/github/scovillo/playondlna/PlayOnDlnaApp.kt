package io.github.scovillo.playondlna

import android.app.Application
import io.github.scovillo.playondlna.dlna.control.PlaybackSessions
import io.github.scovillo.playondlna.server.LocalIpAddress
import io.github.scovillo.playondlna.server.ServerPort

class PlayOnDlnaApp : Application() {
    val playbackSessions = PlaybackSessions()
    val localIpAddress = LocalIpAddress()
    val serverPort = ServerPort()
}
