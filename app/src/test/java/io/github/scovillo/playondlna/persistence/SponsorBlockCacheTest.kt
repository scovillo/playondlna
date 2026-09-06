package io.github.scovillo.playondlna.persistence

import io.github.scovillo.playondlna.dlna.control.SponsorSegment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SponsorBlockCacheTest {
    private lateinit var directory: File
    private var currentTime = 1_000_000L
    private lateinit var cache: SponsorBlockCache

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("sponsorblock-cache-test").toFile()
        cache = SponsorBlockCache(directory, currentTimeMillis = { currentTime })
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun savesAndLoadsSegmentsForVideo() {
        val segments = listOf(SponsorSegment(12.5, 25.0))

        cache.save("dQw4w9WgXcQ", segments)

        assertEquals(segments, cache.load("dQw4w9WgXcQ"))
    }

    @Test
    fun expiresSegmentsAfter24Hours() {
        cache.save("dQw4w9WgXcQ", emptyList())
        currentTime += 24 * 60 * 60 * 1000L

        assertNull(cache.load("dQw4w9WgXcQ"))
    }

    @Test
    fun ignoresDamagedCacheFile() {
        File(directory, "dQw4w9WgXcQ.sponsorblock.json").writeText("not json")

        assertNull(cache.load("dQw4w9WgXcQ"))
    }
}
