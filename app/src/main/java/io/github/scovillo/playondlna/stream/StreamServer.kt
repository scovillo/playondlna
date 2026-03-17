/*
 * PlayOnDlna - An Android application to play media on dlna devices
 * Copyright (C) 2025 Lukas Scheerer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.scovillo.playondlna.stream

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import java.util.Enumeration

fun getRandomFreePort(): Int {
    try {
        ServerSocket(0).use { socket ->
            socket.setReuseAddress(true)
            return socket.getLocalPort()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        return 63791
    }
}

val serverPort = getRandomFreePort()

fun getLocalIpAddress(): String? {
    try {
        val interfaces: Enumeration<NetworkInterface?> = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface? = interfaces.nextElement()
            if (!networkInterface!!.isUp() || networkInterface.isLoopback) {
                continue
            }
            val addresses: Enumeration<InetAddress?> = networkInterface.getInetAddresses()
            while (addresses.hasMoreElements()) {
                val inetAddress: InetAddress? = addresses.nextElement()
                if (!inetAddress!!.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (e: SocketException) {
        e.printStackTrace()
    }
    return null
}

class VideoHttpServer(port: Int) : NanoHTTPD(port) {

    val allFiles = mutableMapOf<String, VideoFile>()

    override fun serve(session: IHTTPSession): Response {
        Log.i("VideoHttpServer", "-> ${session.uri}")
        Log.d(
            "RequestHeaders",
            session.headers.map { "${it.key}: ${it.value}" }.joinToString(System.lineSeparator())
        )
        val uriParts = session.uri.split("/")
        val id = uriParts[1]

        val isSubtitle = session.uri.endsWith(".srt", ignoreCase = true)
        if (isSubtitle) {
            val subtitle =
                allFiles[id]?.subtitle
                    ?: return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "Subtitle not found for video id $id!"
                    )
            val fis = FileInputStream(subtitle.file)
            val response = newFixedLengthResponse(
                Response.Status.OK,
                "text/srt",
                fis,
                subtitle.file.length()
            )
            return response
        }

        val file = allFiles[id]?.value
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Video with id $id not found!"
            )
        val fileLength = file.length()
        val rangeHeader = session.headers["range"]
        try {
            val (start, end) = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader.removePrefix("bytes=").split("-")
                val start = range[0].toLongOrNull() ?: 0L
                val end = range.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1)
                start.coerceAtMost(fileLength - 1) to end.coerceAtMost(fileLength - 1)
            } else {
                0L to (fileLength - 1)
            }
            val contentLength = end - start + 1
            val fis = FileInputStream(file)
            var skipped = 0L
            while (skipped < start) {
                val skipNow = fis.skip(start - skipped)
                if (skipNow <= 0) break
                skipped += skipNow
            }
            val response = if (rangeHeader != null) {
                newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    "video/mp4",
                    fis,
                    contentLength
                ).apply {
                    addHeader("Content-Range", "bytes $start-$end/$fileLength")
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, "video/mp4", fis, contentLength).apply {
                    addHeader(
                        "contentFeatures.dlna.org",
                        "DLNA.ORG_PN=${allFiles[id]!!.videoQuality.dlnaProfile}.;DLNA.ORG_OP=11;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                    )
                    addHeader("transferMode.dlna.org", "Streaming")
                }
            }
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Connection", "keep-alive")
            Log.i("VideoHttpServer", "<- ${session.uri}")
            return response
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "IO Error: ${e.message}"
            )
        }
    }
}

val videoHttpServer = VideoHttpServer(serverPort)

class WebServerService : Service() {

    override fun onCreate() {
        super.onCreate()
        try {
            videoHttpServer.start()
            Log.i("WebServerService", "Http Server started!")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val notification: Notification = NotificationCompat.Builder(this, "http_channel")
            .setContentTitle("HTTP-Streaming active")
            .setContentText("Server running on http://${getLocalIpAddress()}:$serverPort/")
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        videoHttpServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}