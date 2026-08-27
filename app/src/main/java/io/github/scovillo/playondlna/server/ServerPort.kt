package io.github.scovillo.playondlna.server

import java.net.ServerSocket

class ServerPort {
    private val randomPort by lazy {
        randomFreePort()
    }

    fun value(): Int = randomPort

    private fun randomFreePort(): Int =
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }
}

val serverPort = ServerPort().value()
