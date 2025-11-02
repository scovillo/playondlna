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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return "%.1f MB".format(mb)
}

class PlayOnDlnaFileDownload(
    private val url: String,
    private val outputFile: File
) {
    private var _totalSize = -1L
    val totalSize: Long
        get() {
            return _totalSize
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start(onProgress: (totalDownloaded: Long) -> Unit): File = coroutineScope {
        _totalSize = getContentLength(url)
        if (_totalSize <= 0L) throw IOException("Invalid content length for $url")

        val fixedChunkSize = 4L * 1024L * 1024L
        val numThreads = ceil(totalSize.toDouble() / fixedChunkSize).toInt().coerceAtLeast(1)
        val chunks = mutableListOf<File>()
        val chunkProgress = LongArray(numThreads)

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
            "Start download of ${outputFile.name} with $numThreads chunks, totalSize=${
                formatBytes(
                    totalSize
                )
            }, chunkSize=${
                formatBytes(
                    fixedChunkSize
                )
            }"
        )
        val jobs = mutableListOf<Deferred<Unit>>()
        for (i in 0 until numThreads) {
            val start = i * fixedChunkSize
            val end = if (i == numThreads - 1) totalSize - 1 else (start + fixedChunkSize - 1)
            val chunkFile =
                File.createTempFile("${outputFile.name}_chunk_$i", ".tmp", outputFile.parentFile)
            chunks.add(chunkFile)
            jobs.add(async(Dispatchers.IO) { downloadChunkSuspend(start, end, chunkFile, i) })
        }

        jobs.awaitAll()
        mergeChunks(chunks, outputFile)
        chunks.forEach { it.delete() }

        Log.d("Download", "Completed download of ${outputFile.name}")
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

        val videoDl = PlayOnDlnaFileDownload(videoUrl, videoFile)
        val audioDl = PlayOnDlnaFileDownload(audioUrl, audioFile)

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
