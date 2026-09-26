package io.github.scovillo.playondlna.persistence

import io.github.scovillo.playondlna.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class LibraryFileType(
    private val matches: (String) -> Boolean,
) {
    PLAYLISTS({ it == "playlists.json" }),
    METADATA({ it.endsWith(".meta.json") }),
    THUMBNAIL({ it.endsWith(".thumb.jpg") }),
    MP3({ it.endsWith("_final_.mp3") }),
    MP4({ it.endsWith("_muxed_final.mp4") }),
    M4A({ it.endsWith("_muxed_final.m4a") }),
    SUBTITLE({ it.endsWith(".srt") }),
    ;

    companion object {
        fun from(file: File): LibraryFileType? =
            file.takeIf { it.isFile }?.let { candidate ->
                entries.firstOrNull { it.matches(candidate.name) }
            }
    }
}

/** Moves library data from the old cache location to the durable app-files location. */
class LibraryStorageMigration(
    private val legacyCacheDir: File,
    private val libraryDir: File,
) {
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    fun migrate() {
        if (!legacyCacheDir.exists()) {
            AppLog.i("LibraryMigration", "Skipped: legacy cache directory does not exist")
            return
        }
        if (legacyCacheDir.absoluteFile == libraryDir.absoluteFile) {
            AppLog.w("LibraryMigration", "Skipped: legacy and library directories are identical")
            return
        }
        AppLog.i("LibraryMigration", "Starting migration from ${legacyCacheDir.absolutePath} to ${libraryDir.absolutePath}")
        if (!libraryDir.exists() && !libraryDir.mkdirs()) {
            AppLog.e("LibraryMigration", "Failed to create library directory ${libraryDir.absolutePath}")
            return
        }
        val files = legacyCacheDir.listFiles()?.filter { LibraryFileType.from(it) != null }.orEmpty()
        _progress.value = if (files.isEmpty()) 1f else 0f
        var migrated = 0
        var skipped = 0
        var failed = 0
        AppLog.i("LibraryMigration", "Found ${files.size} library files to migrate")
        files.forEachIndexed { index, source ->
            val target = File(libraryDir, source.name)
            if (target.exists()) {
                AppLog.i("LibraryMigration", "Keeping existing ${target.name}")
                skipped++
                _progress.value = (index + 1).toFloat() / files.size
                return@forEachIndexed
            }
            val moved =
                source.renameTo(target) ||
                    runCatching {
                        source.copyTo(target, overwrite = false)
                        source.delete().also { deleted ->
                            if (!deleted) throw IllegalStateException("source was not deleted")
                        }
                    }.onFailure { error ->
                        AppLog.e("LibraryMigration", "Copy fallback failed for ${source.name}", error)
                    }.getOrDefault(false)
            if (!moved) {
                AppLog.e("LibraryMigration", "Failed to migrate ${source.name}")
                target.delete()
                failed++
            } else {
                AppLog.i("LibraryMigration", "Migrated ${source.name}")
                migrated++
            }
            _progress.value = (index + 1).toFloat() / files.size
        }
        AppLog.i("LibraryMigration", "Finished: migrated=$migrated, skipped=$skipped, failed=$failed")
    }
}
