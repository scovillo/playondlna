package io.github.scovillo.playondlna.persistence

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryStorageMigrationTest {
    private lateinit var root: java.nio.file.Path

    @Before
    fun setUp() {
        root = Files.createTempDirectory("library-migration-test")
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun migratesLibraryFilesButKeepsTemporaryFiles() {
        val cache = root.resolve("cache").also { it.createDirectories() }
        val library = root.resolve("files")
        cache.resolve("video.meta.json").writeText("metadata")
        cache.resolve("video_muxed_final.mp4").writeText("media")
        cache.resolve("playlists.json").writeText("[]")
        cache.resolve("download.tmp").writeText("temporary")

        LibraryStorageMigration(cache.toFile(), library.toFile()).migrate()

        assertTrue(library.resolve("video.meta.json").toFile().exists())
        assertTrue(library.resolve("video_muxed_final.mp4").toFile().exists())
        assertTrue(library.resolve("playlists.json").toFile().exists())
        assertFalse(cache.resolve("video.meta.json").toFile().exists())
        assertTrue(cache.resolve("download.tmp").toFile().exists())
    }

    @Test
    fun doesNotOverwriteExistingLibraryFiles() {
        val cache = root.resolve("cache").also { it.createDirectories() }
        val library = root.resolve("files").also { it.createDirectories() }
        cache.resolve("video.meta.json").writeText("old")
        library.resolve("video.meta.json").writeText("new")

        LibraryStorageMigration(cache.toFile(), library.toFile()).migrate()

        assertTrue(cache.resolve("video.meta.json").toFile().exists())
        assertTrue(library.resolve("video.meta.json").toFile().readText() == "new")
    }
}
