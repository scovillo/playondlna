package com.github.scovillo.playondlna.stream

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient()

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val requestBody = this.body(request)
        val reqBuilder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)

        for ((key, value) in request.headers()) {
            reqBuilder.addHeader(key, value.joinToString(";"))
        }

        try {
            val response = client.newCall(reqBuilder.build()).execute()
            val body = response.body?.string()
            Log.i(
                "OkHttpDownloader",
                "${request.httpMethod()} | ${request.url()} | $requestBody - ${response.code} | $body"
            )
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                body,
                response.request.url.toString()
            )
        } catch (e: IOException) {
            throw RuntimeException("HTTP error", e)
        }
    }

    private fun body(request: org.schabi.newpipe.extractor.downloader.Request): RequestBody? {
        if (request.dataToSend() != null) {
            val data: ByteArray = request.dataToSend()!!
            Log.i("Body", String(data, Charsets.UTF_8))
            val contentType =
                request.headers()["Content-Type"]?.first() ?: "application/octet-stream"
            val mediaType = contentType.toMediaTypeOrNull()
            return data.toRequestBody(mediaType)
        } else if (request.httpMethod() == "POST")
            return "".toRequestBody("text/plain".toMediaTypeOrNull())
        return null
    }
}
