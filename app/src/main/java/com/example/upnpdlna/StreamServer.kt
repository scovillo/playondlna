package com.example.upnpdlna

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ServerSocket

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

class VideoHttpServer(port: Int, val dir: File) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        try {
            Log.i("VideoHttpServer", "Received id: ${session.uri.substring(1)}")
            val videoFile = dir.listFiles()!!.find { it.name.contains(session.uri.substring(1)) }!!
            Log.i("VideoHttpServer", "Serving file: ${videoFile.absolutePath}")
            val fis = FileInputStream(videoFile)
            val fileLength = videoFile.length()
            val headers = session.headers
            val range = headers["range"]

            var startFrom: Long = 0
            var endAt = fileLength - 1

            if (range != null && range.startsWith("bytes=")) {
                val ranges: Array<String?> = range.substring("bytes=".length).split("-".toRegex())
                    .dropLastWhile { it.isEmpty() }.toTypedArray()
                try {
                    startFrom = ranges[0]!!.toLong()
                    if (ranges.size > 1) {
                        endAt = ranges[1]!!.toLong()
                    }
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                }
            }

            val contentLength = endAt - startFrom + 1
            fis.skip(startFrom)

            val res: Response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                "video/mp4",
                fis,
                contentLength
            )
            res.addHeader("Content-Length", "$contentLength")
            res.addHeader("Content-Range", "bytes $startFrom-$endAt/$fileLength")
            res.addHeader("Accept-Ranges", "bytes")
            return res
        } catch (e: IOException) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "IO Error"
            )
        }
    }
}

class WebServerService : Service() {
    private var server: VideoHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        server = VideoHttpServer(63791, this.getExternalFilesDir(null)!!)
        try {
            server!!.start()
            Log.i("WebServerService", "Http Server started!")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val notification: Notification = NotificationCompat.Builder(this, "http_channel")
            .setContentTitle("HTTP-Streaming aktiv")
            .setContentText("Server läuft auf Port 63791")
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}