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
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.persistence.LibraryManager
import io.github.scovillo.playondlna.persistence.PlaylistManager
import java.io.ByteArrayInputStream
import java.io.FileInputStream

class MediaHttpServer(
    private val serverPort: Int,
    private val libraryManager: LibraryManager,
    private val playlistManager: PlaylistManager,
    private val defaultCover: ByteArray,
) : NanoHTTPD(serverPort) {
    override fun serve(session: IHTTPSession): Response {
        Log.i("MediaHttpServer", "-> ${session.uri}")
        Log.d(
            "RequestHeaders",
            session.headers.map { "${it.key}: ${it.value}" }.joinToString(System.lineSeparator()),
        )
        val uriParts = session.uri.split("/")
        if (uriParts.size == 4 && uriParts[1] == "playlists" && uriParts[3] == "playlist.m3u") {
            val id = uriParts[2]
            AppLog.i("MediaHttpServer", "Playlist request for $id")
            val playlist =
                playlistManager.getPlaylists().find { it.id == id }
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Playlist not found")
            val payload =
                DlnaPlaylist(
                    playlist,
                    libraryManager.fetchAllItems(),
                    "http://${localIpAddress.value()}:$serverPort",
                ).toPayload()
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Playlist is empty")
            return newFixedLengthResponse(Response.Status.OK, "${payload.mimeType}; charset=UTF-8", payload.content)
        }
        if (uriParts.size == 4 && uriParts[1] == "playlists" && uriParts[3] == "cover.jpg") {
            AppLog.i("MediaHttpServer", "Cover request for playlists")
            return newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(defaultCover),
                defaultCover.size.toLong(),
            )
        }
        val id = uriParts.getOrNull(1) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val item = runCatching { libraryManager.fetchOneItem(id) }.getOrNull()

        if (session.uri.endsWith("/cover.jpg", ignoreCase = true)) {
            AppLog.i("MediaHttpServer", "Cover request for $id")
            item ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cover not found")
            return newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(defaultCover),
                defaultCover.size.toLong(),
            )
        }

        val isSubtitle = session.uri.endsWith(".srt", ignoreCase = true)
        if (isSubtitle) {
            AppLog.d("MediaHttpServer", "Subtitle request for $id")
            val subtitle =
                item?.subtitle
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
        AppLog.i("MediaHttpServer", "Media request for $id")
        item
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Media file with id $id not found!",
            )
        val file = item.mediaFile
        val fileLength = file.length()
        val mimeType = item.mimeType
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
                            "DLNA.ORG_PN=${item.dlnaProfile}.;DLNA.ORG_OP=11;" +
                                "DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
                        )
                        addHeader("transferMode.dlna.org", "Streaming")
                    }
                }
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Connection", "keep-alive")
            Log.i("MediaHttpServer", "<- ${session.uri}")
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

class MediaServerService : Service() {
    private val mediaServerNotification = MediaServerNotification()
    private var mediaHttpServer: MediaHttpServer? = null
    private var isServerStarted = false

    override fun onCreate() {
        AppLog.i("MediaServerService", "onCreate")
        super.onCreate()
        localIpAddress.initialize(getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
        mediaServerNotification.createNotificationChannel(this)
        isServerStarted = startMediaFileServer()
        if (!isServerStarted) {
            stopSelf()
        }
    }

    private fun startMediaFileServer(): Boolean =
        try {
            val defaultCover = resources.openRawResource(R.raw.playlist_album_cover).use { it.readBytes() }
            mediaHttpServer =
                MediaHttpServer(
                    serverPort,
                    LibraryManager(cacheDir),
                    PlaylistManager(cacheDir),
                    defaultCover,
                ).also { it.start() }
            promoteToForeground(
                mediaServerNotification.build(this, getString(R.string.notification_text, localIpAddress.value(), serverPort)),
            )
            Log.i("MediaServerService", "HTTP server started")
            true
        } catch (exception: Exception) {
            Log.e("MediaServerService", "Failed to start HTTP server", exception)
            mediaHttpServer?.stop()
            mediaHttpServer = null
            false
        }

    private fun promoteToForeground(notification: Notification) {
        val isSdkModern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (isSdkModern) {
            AppLog.i("MediaServerService", "startForeground modern with FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK")
            startForeground(mediaServerNotification.id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            return
        } else {
            AppLog.i("MediaServerService", "startForeground oldschool")
            startForeground(mediaServerNotification.id, notification)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == mediaServerNotification.actionStopServer) {
            AppLog.i("MediaServerService", "Stopping service cause of notification action")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isServerStarted) {
            AppLog.i("MediaServerService", "Stopping service because server failed to start")
            stopSelf()
            return START_NOT_STICKY
        }
        AppLog.i("MediaServerService", "Start sticky")
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.i("MediaServerService", "onDestroy")
        mediaHttpServer?.stop()
        mediaHttpServer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        AppLog.i("MediaServerService", "onBind")
        return null
    }
}
