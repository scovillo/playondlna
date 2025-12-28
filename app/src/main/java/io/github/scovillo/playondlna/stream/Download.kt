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

import android.util.Log
import io.github.scovillo.playondlna.model.VideoJobState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

val okHttpClient = OkHttpClient.Builder()
    .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
    .retryOnConnectionFailure(true)
    .build()


private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return "%.1f MB".format(mb)
}

class PlayOnDlnaFileDownload(
    val url: String,
    val userAgent: String,
    private val outputFile: File,
    private val chunkCalculation: ChunkCalculation,
    private val client: OkHttpClient,
) {
    private lateinit var chunkProgress: LongArray
    private var _totalSize = 1L
    val totalSize: Long get() = _totalSize

    suspend fun start(
        onProgress: (totalDownloaded: Long) -> Unit
    ): File = coroutineScope {

        _totalSize = getContentLengthViaRange(url)
        if (_totalSize <= 0L) throw IOException("Invalid content length")

        val chunks = chunkCalculation.chunks(totalSize)
        Log.d(
            "PlayOnDlnaFileDownload",
            "Spawning with user-agent='$userAgent', threads=${chunks.size} and chunks=$chunks"
        )
        chunkProgress = LongArray(chunks.size)
        val chunkFiles = mutableListOf<File>()

        val jobs = chunks.mapIndexed { index, it ->

            val file = File.createTempFile("${outputFile.name}_$index", ".part")
            chunkFiles += file

            async(Dispatchers.IO) {
                downloadChunk(
                    url = url,
                    start = it.start,
                    end = it.end,
                    file = file
                ) { bytesRead ->
                    chunkProgress[index] = bytesRead
                    onProgress(chunkProgress.sum())
                }
            }
        }

        jobs.awaitAll()

        mergeChunks(chunkFiles, outputFile)
        chunkFiles.forEach { it.delete() }

        outputFile
    }

    suspend fun downloadChunk(
        url: String,
        start: Long,
        end: Long,
        file: File,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                userAgent
            )
            .header("Accept", "*/*")
            .header("Range", "bytes=$start-$end")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty body")

            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var total = 0L

                    while (true) {
                        read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
            }
        }
    }

    private fun getContentLengthViaRange(url: String): Long {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                userAgent
            )
            .header("Range", "bytes=0-0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val range = response.header("Content-Range")
            return range?.substringAfter("/")?.toLongOrNull() ?: -1
        }
    }

    private fun mergeChunks(chunks: List<File>, output: File) {
        FileOutputStream(output).use { out ->
            chunks.forEach { it.inputStream().use { input -> input.copyTo(out) } }
        }
    }
}

class PlayOnDlnaVideoStream(val videoFile: File, val audioFile: File) {
    fun delete() {
        videoFile.delete()
        audioFile.delete()
    }
}

class PlayOnDlnaStreamDownload(
    private val id: String,
    private val videoUrl: String,
    private val audioUrl: String,
    private val cacheDir: File,
    private val state: VideoJobState,
    val logTimeInMillis: Int = 3000
) {
    suspend fun startDownload(): PlayOnDlnaVideoStream = coroutineScope {
        val videoFile = File.createTempFile("${id}_video_", ".tmp", cacheDir)
        val audioFile = File.createTempFile("${id}_audio_", ".tmp", cacheDir)

        val userAgent = YoutubeParsingHelper.getAndroidUserAgent(null)
        val videoDl = PlayOnDlnaFileDownload(
            videoUrl,
            userAgent,
            videoFile,
            ChunkCalculation(24, 8 * 1024 * 1024),
            okHttpClient
        )
        val audioDl = PlayOnDlnaFileDownload(
            audioUrl,
            userAgent,
            audioFile,
            ChunkCalculation(8, 4 * 1024 * 1024),
            okHttpClient
        )

        val videoProgress = LongArray(1)
        val audioProgress = LongArray(1)

        val startTime = System.currentTimeMillis()
        val progressJob = launch(Dispatchers.Main) {
            var lastTotal = 0L
            var lastLogTime = startTime

            while (isActive) {
                delay(20L)

                val totalDownloaded = videoProgress[0] + audioProgress[0]
                val totalSize = videoDl.totalSize + audioDl.totalSize
                val progressPercent =
                    (totalDownloaded.toDouble() * 100 / totalSize).toFloat().coerceIn(0.0f, 100.0f)

                state.updateProgress(progressPercent)

                val now = System.currentTimeMillis()
                if (now - lastLogTime >= logTimeInMillis) {
                    val delta = totalDownloaded - lastTotal
                    val elapsedSec = (now - lastLogTime) / 1000L
                    val speed = delta.toDouble() / (1024 * 1024) / elapsedSec
                    val totalElapsedSec = (now - startTime) / 1000L
                    val avgSpeed = totalDownloaded.toDouble() / (1024 * 1024) / totalElapsedSec
                    Log.d(
                        "Download",
                        "Progress: %.1f%%, Downloaded: %s, Speed: %.2f MB/s, Avg: %.2f MB/s".format(
                            progressPercent,
                            formatBytes(totalDownloaded),
                            speed,
                            avgSpeed
                        )
                    )
                    lastTotal = totalDownloaded
                    lastLogTime = now
                }
            }
        }
        val jobs = listOf(
            async(Dispatchers.IO) { videoDl.start { videoProgress[0] = it } },
            async(Dispatchers.IO) { audioDl.start { audioProgress[0] = it } }
        )
        val results = jobs.awaitAll()
        progressJob.cancelAndJoin()
        Log.d(
            "Download",
            "Download in ${(System.currentTimeMillis() - startTime) / 1000}s completed: Video -> $videoFile, Audio -> $audioFile"
        )
        return@coroutineScope PlayOnDlnaVideoStream(videoFile = results[0], audioFile = results[1])
    }
}
