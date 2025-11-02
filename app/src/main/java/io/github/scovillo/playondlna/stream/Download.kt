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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return "%.1f MB".format(mb)
}

class PlayOnDlnaFileDownload(
    private val url: String,
    private val outputFile: File,
    private val maxThreads: Int = 42,
    private val chunkSizeBytes: Long = 11L * 1024 * 1024
) {
    private lateinit var chunkProgress: LongArray
    private var _totalSize = 1L

    val totalSize: Long
        get() {
            return _totalSize
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start(onProgress: (totalDownloaded: Long) -> Unit): File = coroutineScope {
        _totalSize = getContentLength(url)
        val numThreads =
            ((totalSize + chunkSizeBytes - 1) / chunkSizeBytes).toInt().coerceAtMost(maxThreads)
        chunkProgress = LongArray(numThreads)
        val actualChunkSize = totalSize / numThreads

        val chunks = mutableListOf<File>()

        suspend fun downloadChunkSuspend(start: Long, end: Long, file: File, index: Int) =
            suspendCancellableCoroutine { cont ->
                downloadChunk(url, start, end, file) { bytesRead ->
                    chunkProgress[index] = bytesRead
                    val totalDownloaded = chunkProgress.sum()
                    onProgress(totalDownloaded)
                    if (bytesRead >= (end - start + 1)) cont.resume(Unit) {}
                }
            }
        Log.d(
            "Download",
            "Start download of ${outputFile.name} with $numThreads threads, totalSize=${
                formatBytes(
                    totalSize
                )
            }, chunkSize=${formatBytes(actualChunkSize)}"
        )
        val jobs = mutableListOf<Deferred<Unit>>()
        for (i in 0 until numThreads) {
            val start = i * actualChunkSize
            val end = if (i == numThreads - 1) totalSize - 1 else (start + actualChunkSize - 1)
            val chunkFile =
                File.createTempFile("${outputFile.name}_chunk_$i", ".tmp", outputFile.parentFile)
            chunks.add(chunkFile)
            jobs.add(async(Dispatchers.IO) { downloadChunkSuspend(start, end, chunkFile, i) })
        }

        jobs.awaitAll()
        mergeChunks(chunks, outputFile)
        chunks.forEach { it.delete() }
        return@coroutineScope outputFile
    }


    private fun downloadChunk(
        url: String,
        start: Long,
        end: Long,
        file: File,
        onProgress: (bytesRead: Long) -> Unit
    ) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", "bytes=$start-$end")
        conn.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                var totalRead = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalRead += read
                    onProgress(totalRead)
                }
            }
        }
        conn.disconnect()
    }

    private fun getContentLength(url: String): Long {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        val length = conn.getHeaderFieldLong("Content-Length", -1)
        conn.disconnect()
        return length
    }

    private fun mergeChunks(chunks: List<File>, output: File) {
        FileOutputStream(output).use { out ->
            chunks.forEach { chunk ->
                FileInputStream(chunk).use { input ->
                    input.copyTo(out)
                }
            }
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
    private val state: VideoJobState
) {
    suspend fun startDownload(): PlayOnDlnaVideoStream = coroutineScope {
        val videoFile = File.createTempFile("${id}_video_", ".tmp", cacheDir)
        val audioFile = File.createTempFile("${id}_audio_", ".tmp", cacheDir)

        val videoDl = PlayOnDlnaFileDownload(videoUrl, videoFile, 46)
        val audioDl = PlayOnDlnaFileDownload(audioUrl, audioFile, 4)

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
                if (now - lastLogTime >= 1000L) {
                    val delta = totalDownloaded - lastTotal
                    val speed = delta.toDouble() / (1024 * 1024)
                    val elapsedSec = (now - startTime) / 1000.0
                    val avgSpeed = totalDownloaded.toDouble() / (1024 * 1024) / elapsedSec
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
