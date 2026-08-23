package io.github.scovillo.playondlna.persistence

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlaylistManagerTest {
    private lateinit var directory: File
    private lateinit var manager: PlaylistManager

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("playlist-manager-test").toFile()
        manager = PlaylistManager(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun createsRenamesAndDeletesPlaylist() {
        val playlist = manager.createPlaylist(" Weekend ")
        assertNotNull(playlist)
        assertEquals("Weekend", manager.getPlaylists().single().name)

        assertTrue(manager.renamePlaylist(playlist!!.id, "Favorites"))
        assertEquals("Favorites", manager.getPlaylists().single().name)

        assertTrue(manager.deletePlaylist(playlist.id))
        assertTrue(manager.getPlaylists().isEmpty())
    }

    @Test
    fun addsVideoOnlyOnceAndRemovesIt() {
        val playlist = manager.createPlaylist("Favorites")!!

        assertTrue(manager.addVideo(playlist.id, "video-1"))
        assertFalse(manager.addVideo(playlist.id, "video-1"))
        assertEquals(listOf("video-1"), manager.getPlaylists().single().videoIds)

        assertTrue(manager.removeVideo(playlist.id, "video-1"))
        assertTrue(manager.getPlaylists().single().videoIds.isEmpty())
    }

    @Test
    fun savesAndLoadsIncludingReferencesToMissingVideos() {
        val playlist = manager.createPlaylist("Archive")!!
        assertTrue(manager.addVideo(playlist.id, "missing-video"))

        val reloadedManager = PlaylistManager(directory)
        assertEquals(listOf("missing-video"), reloadedManager.getPlaylists().single().videoIds)
        assertTrue(reloadedManager.removeVideo(playlist.id, "missing-video"))
    }

    @Test
    fun returnsEmptyListForMissingEmptyOrDamagedFile() {
        assertTrue(manager.getPlaylists().isEmpty())
        File(directory, "playlists.json").writeText("")
        assertTrue(manager.getPlaylists().isEmpty())
        File(directory, "playlists.json").writeText("not json")
        assertTrue(manager.getPlaylists().isEmpty())
    }
}
