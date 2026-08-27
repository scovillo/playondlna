package io.github.scovillo.playondlna.persistence

import android.util.Log
import io.github.scovillo.playondlna.model.LibraryMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LibraryManager(private val cacheDir: File) {
    private val client = OkHttpClient()

    fun saveMetadata(metadata: LibraryMetadata) {
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
                Log.e("LibraryManager", "Failed to download thumbnail for $id", e)
            }
            null
        }

    fun thumbnailFile(id: String): File? = File(cacheDir, "$id.thumb.jpg").takeIf { it.exists() && it.length() > 0 }

    fun getLibraryItems(): List<LibraryItem> {
        val items = mutableListOf<LibraryItem>()
        val files = cacheDir.listFiles() ?: return emptyList()

        val metaFiles = files.filter { it.name.endsWith(".meta.json") }
        for (metaFile in metaFiles) {
            try {
                val metadata = LibraryMetadata.fromJson(metaFile.readText())
                val videoFile =
                    files.find {
                        it.name.contains(metadata.id) && it.name.contains("final") &&
                            (it.name.endsWith(".mp4") || it.name.endsWith(".m4a") || it.name.endsWith(".mp3"))
                    }
                if (videoFile != null) {
                    val thumbFile = thumbnailFile(metadata.id)
                    items.add(LibraryItem(metadata, videoFile, thumbFile, videoFile.length()))
                }
            } catch (e: Exception) {
                Log.e("LibraryManager", "Error loading library item from ${metaFile.name}", e)
            }
        }
        return items.sortedByDescending { it.videoFile.lastModified() }
    }
}

data class LibraryItem(
    val metadata: LibraryMetadata,
    val videoFile: File,
    val thumbnailFile: File?,
    val sizeInBytes: Long,
)
