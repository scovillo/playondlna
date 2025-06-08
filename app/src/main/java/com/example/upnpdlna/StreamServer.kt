package com.example.upnpdlna

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException


class VideoHttpServer(port: Int, private val videoFile: File) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        try {
            val fis = FileInputStream(videoFile)
            val fileLength = videoFile.length()
            val headers = session.getHeaders()
            val range = headers.get("range")

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
                } catch (ignored: NumberFormatException) {
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
            res.addHeader("Content-Length", "" + contentLength)
            res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLength)
            res.addHeader("Accept-Ranges", "bytes")
            return res
        } catch (e: IOException) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "IO Error"
            )
        }
    }
}