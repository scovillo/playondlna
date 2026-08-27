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

package io.github.scovillo.playondlna.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.model.LibraryItem
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException

class MediaFileHttpServer(private val serverPort: Int) : NanoHTTPD(serverPort) {
    val allFiles = mutableMapOf<String, LibraryItem>()
    var playlistProvider: ((String, String) -> DlnaPlaylist.Payload?)? = null
    var playlistCoverProvider: ((String) -> ByteArray?)? = null
    var audioCoverProvider: (() -> ByteArray?)? = null

    override fun serve(session: IHTTPSession): Response {
        Log.i("MediaFileHttpServer", "-> ${session.uri}")
        Log.d(
            "RequestHeaders",
            session.headers.map { "${it.key}: ${it.value}" }.joinToString(System.lineSeparator()),
        )
        val uriParts = session.uri.split("/")
        if (uriParts.size == 4 && uriParts[1] == "playlists" && uriParts[3] == "playlist.m3u") {
            val playlist =
                playlistProvider?.invoke(
                    uriParts[2],
                    "http://${localIpAddress.value()}:$serverPort",
                )
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Playlist not found or empty")
            return newFixedLengthResponse(Response.Status.OK, "${playlist.mimeType}; charset=UTF-8", playlist.content)
        }
        if (uriParts.size == 4 && uriParts[1] == "playlists" && uriParts[3] == "cover.jpg") {
            val cover =
                playlistCoverProvider?.invoke(uriParts[2])
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Playlist cover not found")
            return newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(cover),
                cover.size.toLong(),
            )
        }
        val id = uriParts.getOrNull(1) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

        if (session.uri.endsWith("/cover.jpg", ignoreCase = true)) {
            allFiles[id] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cover not found")
            val cover =
                audioCoverProvider?.invoke()
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cover not found")
            return newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(cover),
                cover.size.toLong(),
            )

        }

        val isSubtitle = session.uri.endsWith(".srt", ignoreCase = true)
        if (isSubtitle) {
            val subtitle =
                allFiles[id]?.subtitle
                    ?: return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "Subtitle not found for video id $id!",
                    )
            val fis = FileInputStream(subtitle.file)
            val response =
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/srt",
                    fis,
                    subtitle.file.length(),
                )
            return response
        }

        val file =
            allFiles[id]?.mediaFile
                ?: return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Media file with id $id not found!",
                )
        val fileLength = file.length()
        val mimeType = allFiles[id]!!.mimeType
        val rangeHeader = session.headers["range"]
        try {
            val (start, end) =
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
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
            val response =
                if (rangeHeader != null) {
                    newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        mimeType,
                        fis,
                        contentLength,
                    ).apply {
                        addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    }
                } else {
                    newFixedLengthResponse(Response.Status.OK, mimeType, fis, contentLength).apply {
                        addHeader(
                            "contentFeatures.dlna.org",
                            "DLNA.ORG_PN=${allFiles[id]!!.dlnaProfile}.;DLNA.ORG_OP=11;" +
                                "DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
                        )
                        addHeader("transferMode.dlna.org", "Streaming")
                    }
                }
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Connection", "keep-alive")
            Log.i("MediaFileHttpServer", "<- ${session.uri}")
            return response
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "IO Error: ${e.message}",
            )
        }
    }
}

val mediaFileHttpServer = MediaFileHttpServer(serverPort)

const val ACTION_STOP_SERVER = "io.github.scovillo.playondlna.server.ACTION_STOP_SERVER"

class MediaFileServerService : Service() {
    override fun onCreate() {
        super.onCreate()
        try {
            mediaFileHttpServer.start()
            Log.i("MediaFileServerService", "Http Server started!")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val stopIntent =
            Intent(this, MediaFileServerService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val notification: Notification =
            NotificationCompat.Builder(this, "http_channel")
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text, localIpAddress.value(), serverPort))
                .setSmallIcon(R.drawable.playondlna_icon)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.stop),
                    stopPendingIntent,
                )
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP_SERVER) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaFileHttpServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
