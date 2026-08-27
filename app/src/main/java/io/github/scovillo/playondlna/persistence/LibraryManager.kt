package io.github.scovillo.playondlna.persistence

import android.util.Log
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryMetadata
import io.github.scovillo.playondlna.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class LibraryManager(private val cacheDir: File) {
    private val client = OkHttpClient()

    fun isExisting(id: String) = File(cacheDir, "$id.meta.json").exists()

    fun save(metadata: LibraryMetadata) {
        val file = File(cacheDir, "${metadata.id}.meta.json")
        try {
            file.writeText(metadata.toJson())
        } catch (e: IOException) {
            Log.e("LibraryManager", "Failed to save metadata for ${metadata.id}", e)
        }
    }

    suspend fun downloadThumbnail(
        id: String,
        url: String,
    ): File? =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, "$id.thumb.jpg")
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.let { body ->
                        FileOutputStream(file).use { output ->
                            body.byteStream().copyTo(output)
                        }
                        return@withContext file
                    }
                }
            } catch (e: IOException) {
                Log.e("LibraryManager", "Failed to download fetchThumbnail for $id", e)
            }
            null
        }

    fun fetchThumbnail(id: String): File? = File(cacheDir, "$id.thumb.jpg").takeIf { it.exists() && it.length() > 0 }

    fun fetchSubtitle(id: String): Subtitle? {
        val file = cacheDir.listFiles()?.find {
            it.exists()
                && it.name.contains(id)
                && it.name.contains("fetchSubtitle")
                && it.name.endsWith(".srt")
        }
        return if (file != null) Subtitle(file) else null
    }

    fun fetchOneItem(id: String): LibraryItem {
        val metadataFile = File(cacheDir, "$id.meta.json")
        if (!metadataFile.exists()) {
            throw FileNotFoundException("Error loading item metadata with id $id")
        }
        val files = cacheDir.listFiles() ?: throw FileNotFoundException("Error loading cache files for id $id")
        return this.buildOneItem(metadataFile, files)
    }

    fun fetchAllItems(): List<LibraryItem> {
        val results = mutableListOf<LibraryItem>()
        val files = cacheDir.listFiles() ?: return emptyList()
        val metaFiles = files.filter { it.name.endsWith(".meta.json") }
        for (metaFile in metaFiles) {
            try {
                val item = this.buildOneItem(metaFile, files)
                results.add(item)
            } catch (e: Exception) {
                Log.e("LibraryManager", "Error loading library item from ${metaFile.name}", e)
            }
        }
        return results.sortedByDescending { it.mediaFile.lastModified() }
    }

    private fun buildOneItem(metadataFile: File, cacheFiles: Array<File>): LibraryItem {
        val metadata = LibraryMetadata.fromJson(metadataFile.readText())
        val mediaFile = cacheFiles
            .find {
                it.name.contains(metadata.id) && it.name.contains("final") &&
                    (it.name.endsWith(".mp4") || it.name.endsWith(".m4a") || it.name.endsWith(".mp3"))
            }
        if (mediaFile == null) {
            throw FileNotFoundException("Error loading media file for id ${metadata.id}")
        }
        val subtitle = fetchSubtitle(metadata.id)
        val thumbnail = fetchThumbnail(metadata.id)
        return LibraryItem(metadata, mediaFile, thumbnail, subtitle)
    }
}

