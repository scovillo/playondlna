package io.github.scovillo.playondlna.model

import io.github.scovillo.playondlna.stream.ChunkCalculation
import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkCalculationTest {

    @Test
    fun matchingTotalLength() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L,
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L)
        assertEquals(3, chunks.size)

        assertEquals(0, chunks[0].start)
        assertEquals(2 * 1024 * 1024 - 1, chunks[0].end)
        assertEquals(2 * 1024 * 1024, chunks[0].totalBytes)

        assertEquals(2 * 1024 * 1024, chunks[1].start)
        assertEquals(4 * 1024 * 1024 - 1, chunks[1].end)
        assertEquals(2 * 1024 * 1024, chunks[1].totalBytes)

        assertEquals(4 * 1024 * 1024, chunks[2].start)
        assertEquals(6 * 1024 * 1024 - 1, chunks[2].end)
        assertEquals(2 * 1024 * 1024, chunks[2].totalBytes)
    }

    @Test
    fun greaterTotalLength1() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L + 100)
        assertEquals(3, chunks.size)

        assertEquals(0, chunks[0].start)
        assertEquals(2 * 1024 * 1024 + 33, chunks[0].end)
        assertEquals(2 * 1024 * 1024 + 34, chunks[0].totalBytes)

        assertEquals(2 * 1024 * 1024 + 34, chunks[1].start)
        assertEquals(4 * 1024 * 1024 + 66, chunks[1].end)
        assertEquals(2 * 1024 * 1024 + 33, chunks[1].totalBytes)

        assertEquals(4 * 1024 * 1024 + 67, chunks[2].start)
        assertEquals(6 * 1024 * 1024 + 99, chunks[2].end)
        assertEquals(2 * 1024 * 1024 + 33, chunks[2].totalBytes)
    }

    @Test
    fun greaterTotalLength2() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L + 1)
        assertEquals(3, chunks.size)

        assertEquals(0, chunks[0].start)
        assertEquals(2 * 1024 * 1024, chunks[0].end)
        assertEquals(2 * 1024 * 1024 + 1, chunks[0].totalBytes)

        assertEquals(2 * 1024 * 1024 + 1, chunks[1].start)
        assertEquals(4 * 1024 * 1024, chunks[1].end)
        assertEquals(2 * 1024 * 1024, chunks[1].totalBytes)

        assertEquals(4 * 1024 * 1024 + 1, chunks[2].start)
        assertEquals(6 * 1024 * 1024, chunks[2].end)
        assertEquals(2 * 1024 * 1024, chunks[2].totalBytes)
    }

    @Test
    fun lowerTotalLength1() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L - 100)
        assertEquals(2, chunks.size)

        assertEquals(0, chunks[0].start)
        assertEquals(3145677, chunks[0].end)
        assertEquals(3145678, chunks[0].totalBytes)

        assertEquals(3145678, chunks[1].start)
        assertEquals(6291355, chunks[1].end)
        assertEquals(3145678, chunks[1].totalBytes)
    }

    @Test
    fun lowerTotalLength2() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L - 1)
        assertEquals(2, chunks.size)

        assertEquals(0, chunks[0].start)
        assertEquals(3145727, chunks[0].end)
        assertEquals(3145728, chunks[0].totalBytes)

        assertEquals(3145728, chunks[1].start)
        assertEquals(6291454, chunks[1].end)
        assertEquals(3145727, chunks[1].totalBytes)
    }
}
