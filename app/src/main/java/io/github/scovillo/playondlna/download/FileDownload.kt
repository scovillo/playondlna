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

package io.github.scovillo.playondlna.download

import android.util.Log
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.preparation.VideoJobState
import io.github.scovillo.playondlna.server.Subtitle
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
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

val okHttpClient =
    OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return "%.1f MB".format(mb)
}

class PlayOnDlnaFileDownload(
    val filePrefix: String,
    val fileSuffix: String,
    val url: String,
    val userAgent: String,
    private val chunkCalculation: ChunkCalculation,
    private val client: OkHttpClient,
    private val cacheDir: File,
) {
    private lateinit var chunkProgress: LongArray
    private lateinit var outputFile: File
    private var _totalSize = 1L
    val totalSize: Long get() = _totalSize
    val result: File get() = outputFile

    suspend fun start(onProgress: (totalDownloaded: Long) -> Unit): File =
        coroutineScope {
            outputFile = File.createTempFile(filePrefix, fileSuffix, cacheDir)
            _totalSize = getContentLengthViaRange(url)
            if (_totalSize <= 0L) throw IOException("Invalid content length")

            val chunks = chunkCalculation.chunks(totalSize)
            Log.d(
                "PlayOnDlnaFileDownload",
                "Spawning for ${outputFile.name} ($totalSize Bytes) with user-agent='$userAgent', threads=${chunks.size} and chunks=$chunks",
            )
            chunkProgress = LongArray(chunks.size)
            val chunkFiles = mutableListOf<File>()

            val jobs =
                chunks.mapIndexed { index, it ->

                    val file = File.createTempFile("${outputFile.name}_$index", ".part")
                    chunkFiles += file

                    async(Dispatchers.IO) {
                        downloadChunk(
                            url = url,
                            start = it.start,
                            end = it.end,
                            file = file,
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
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request =
            Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    userAgent,
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
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-0")
                .build()
        client.newCall(request).execute().use { response ->
            val range = response.header("Content-Range")
            if (range != null) {
                val total = range.substringAfter("/").toLongOrNull()
                if (total != null) return total
            }
            val length = response.header("Content-Length")?.toLongOrNull()
            if (length != null) return length
            return -1
        }
    }

    private fun mergeChunks(
        chunks: List<File>,
        output: File,
    ) {
        FileOutputStream(output).use { out ->
            chunks.forEach { it.inputStream().use { input -> input.copyTo(out) } }
        }
    }
}

class PlayOnDlnaVideoInput(
    val videoFile: File?,
    val audioFile: File?,
    val subtitle: Subtitle?,
) {
    fun delete() {
        AppLog.i("PlayOnDlnaVideoInput", "Deleting $videoFile, $audioFile")
        videoFile?.delete()
        audioFile?.delete()
    }
}

class PlayOnDlnaStreamDownload(
    private val id: String,
    videoUrl: String?,
    private val cacheDir: File,
    private val state: VideoJobState,
    val logTimeInMillis: Int = 3000,
    val userAgent: String = YoutubeParsingHelper.getAndroidUserAgent(null),
) {
    val downloads: MutableMap<String, PlayOnDlnaFileDownload> = mutableMapOf()

    init {
        if (videoUrl != null) {
            downloads["video"] =
                PlayOnDlnaFileDownload(
                    "${id}_video_",
                    ".tmp",
                    videoUrl,
                    userAgent,
                    ChunkCalculation(16, 8 * 1024 * 1024),
                    okHttpClient,
                    cacheDir,
                )
        }
    }

    fun withAudioStream(audioUrl: String) {
        downloads["audio"] =
            PlayOnDlnaFileDownload(
                "${id}_audio_",
                ".tmp",
                audioUrl,
                userAgent,
                ChunkCalculation(6, 4 * 1024 * 1024),
                okHttpClient,
                cacheDir,
            )
    }

    fun withSubtitle(subtitleStream: SubtitlesStream) {
        downloads["subtitle"] =
            PlayOnDlnaFileDownload(
                "${id}_subtitle_",
                ".${subtitleStream.locale.language}.srt",
                subtitleStream.content,
                userAgent,
                ChunkCalculation(2, 4 * 1024 * 1024),
                okHttpClient,
                cacheDir,
            )
    }

    suspend fun start(): PlayOnDlnaVideoInput =
        coroutineScope {
            val progress = LongArray(downloads.size)
            val startTime = System.currentTimeMillis()
            val progressJob =
                launch(Dispatchers.Main) {
                    var lastTotal = 0L
                    var lastLogTime = startTime

                    while (isActive) {
                        delay(20L)

                        val totalDownloaded = progress.sum()
                        val totalSize = downloads.values.sumOf { it.totalSize }
                        val progressPercent =
                            (totalDownloaded.toDouble() * 100 / totalSize).toFloat().coerceIn(0.0f, 100.0f)

                        state.updateDownloadProgress(progressPercent)

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
                                    avgSpeed,
                                ),
                            )
                            lastTotal = totalDownloaded
                            lastLogTime = now
                        }
                    }
                }
            val jobs =
                downloads.values.mapIndexed { index, job ->
                    async(Dispatchers.IO) {
                        job.start { progress[index] = it }
                    }
                }
            jobs.awaitAll()
            progressJob.cancelAndJoin()
            if (downloads.isEmpty()) {
                Log.d("Download", "No local downloads required; FFmpeg will read the remote stream directly")
            } else {
                Log.d(
                    "Download",
                    "Download in ${(System.currentTimeMillis() - startTime) / 1000}s completed: Video -> ${downloads["video"]?.result}," +
                        " Audio -> ${downloads["audio"]?.result}, Subtitle -> ${downloads["subtitle"]?.result}",
                )
            }
            return@coroutineScope PlayOnDlnaVideoInput(
                videoFile = downloads["video"]?.result,
                audioFile = downloads["audio"]?.result,
                subtitle = if (downloads.containsKey("subtitle")) Subtitle(downloads["subtitle"]!!.result) else null,
            )
        }
}
