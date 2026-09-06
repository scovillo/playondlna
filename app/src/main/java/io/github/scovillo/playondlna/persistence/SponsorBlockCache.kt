package io.github.scovillo.playondlna.persistence

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.dlna.control.SponsorSegment
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

class SponsorBlockCache(
    private val cacheDir: File,
    val cacheDurationMs: Long = TimeUnit.HOURS.toMillis(24),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun load(videoId: String): List<SponsorSegment>? {
        val file = cacheFile(videoId)
        if (!file.exists()) {
            AppLog.i("SponsorBlockCache", "No cache entry for $videoId")
            return null
        }
        if (currentTimeMillis() - file.lastModified() >= cacheDurationMs) {
            AppLog.i("SponsorBlockCache", "Cache expired for $videoId")
            return null
        }
        return try {
            val segments = JSONArray(file.readText())
            val cachedSegments =
                List(segments.length()) { index ->
                    val range = segments.getJSONArray(index)
                    SponsorSegment(range.getDouble(0), range.getDouble(1))
                }.filter { it.start >= 0 && it.end > it.start }
            AppLog.i("SponsorBlockCache", "Loaded ${cachedSegments.size} segments from cache for $videoId")
            cachedSegments
        } catch (exception: Exception) {
            AppLog.e("SponsorBlockCache", "Failed to load SponsorBlock segments for $videoId", exception)
            null
        }
    }

    fun save(
        videoId: String,
        segments: List<SponsorSegment>,
    ) {
        try {
            cacheDir.mkdirs()
            val file = cacheFile(videoId)
            val temporaryFile = File(cacheDir, "$videoId.sponsorblock.json.tmp")
            temporaryFile.writeText(JSONArray(segments.map { JSONArray(listOf(it.start, it.end)) }).toString())
            if (!temporaryFile.renameTo(file)) {
                temporaryFile.copyTo(file, overwrite = true)
                temporaryFile.delete()
            }
            file.setLastModified(currentTimeMillis())
        } catch (exception: Exception) {
            AppLog.e("SponsorBlockCache", "Failed to save SponsorBlock segments for $videoId", exception)
        }
    }

    private fun cacheFile(videoId: String) = File(cacheDir, "$videoId.sponsorblock.json")
}
