package io.github.scovillo.playondlna.model

import io.github.scovillo.playondlna.stream.ChunkCalculation
import org.junit.Assert
import org.junit.Test

class ChunkCalculationTest {

    @Test
    fun matchingTotalLength() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L,
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L)
        Assert.assertEquals(3, chunks.size)

        Assert.assertEquals(0, chunks[0].start)
        Assert.assertEquals(2 * 1024 * 1024 - 1, chunks[0].end)
        Assert.assertEquals(2 * 1024 * 1024, chunks[0].totalBytes)

        Assert.assertEquals(2 * 1024 * 1024, chunks[1].start)
        Assert.assertEquals(4 * 1024 * 1024 - 1, chunks[1].end)
        Assert.assertEquals(2 * 1024 * 1024, chunks[1].totalBytes)

        Assert.assertEquals(4 * 1024 * 1024, chunks[2].start)
        Assert.assertEquals(6 * 1024 * 1024 - 1, chunks[2].end)
        Assert.assertEquals(2 * 1024 * 1024, chunks[2].totalBytes)
    }

    @Test
    fun greaterTotalLength1() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L + 100)
        Assert.assertEquals(3, chunks.size)

        Assert.assertEquals(0, chunks[0].start)
        Assert.assertEquals(2 * 1024 * 1024 + 33, chunks[0].end)
        Assert.assertEquals(2 * 1024 * 1024 + 34, chunks[0].totalBytes)

        Assert.assertEquals(2 * 1024 * 1024 + 34, chunks[1].start)
        Assert.assertEquals(4 * 1024 * 1024 + 66, chunks[1].end)
        Assert.assertEquals(2 * 1024 * 1024 + 33, chunks[1].totalBytes)

        Assert.assertEquals(4 * 1024 * 1024 + 67, chunks[2].start)
        Assert.assertEquals(6 * 1024 * 1024 + 99, chunks[2].end)
        Assert.assertEquals(2 * 1024 * 1024 + 33, chunks[2].totalBytes)
    }

    @Test
    fun greaterTotalLength2() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L + 1)
        Assert.assertEquals(3, chunks.size)

        Assert.assertEquals(0, chunks[0].start)
        Assert.assertEquals(2 * 1024 * 1024, chunks[0].end)
        Assert.assertEquals(2 * 1024 * 1024 + 1, chunks[0].totalBytes)

        Assert.assertEquals(2 * 1024 * 1024 + 1, chunks[1].start)
        Assert.assertEquals(4 * 1024 * 1024, chunks[1].end)
        Assert.assertEquals(2 * 1024 * 1024, chunks[1].totalBytes)

        Assert.assertEquals(4 * 1024 * 1024 + 1, chunks[2].start)
        Assert.assertEquals(6 * 1024 * 1024, chunks[2].end)
        Assert.assertEquals(2 * 1024 * 1024, chunks[2].totalBytes)
    }

    @Test
    fun lowerTotalLength1() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L - 100)
        Assert.assertEquals(2, chunks.size)

        Assert.assertEquals(0, chunks[0].start)
        Assert.assertEquals(3145677, chunks[0].end)
        Assert.assertEquals(3145678, chunks[0].totalBytes)

        Assert.assertEquals(3145678, chunks[1].start)
        Assert.assertEquals(6291355, chunks[1].end)
        Assert.assertEquals(3145678, chunks[1].totalBytes)
    }

    @Test
    fun lowerTotalLength2() {
        val classUnderTest = ChunkCalculation(
            maxThreads = 8,
            minSizeInBytes = 2 * 1024 * 1024L
        )

        val chunks = classUnderTest.chunks(6 * 1024 * 1024L - 1)
        Assert.assertEquals(2, chunks.size)

        Assert.assertEquals(0, chunks[0].start)
        Assert.assertEquals(3145727, chunks[0].end)
        Assert.assertEquals(3145728, chunks[0].totalBytes)

        Assert.assertEquals(3145728, chunks[1].start)
        Assert.assertEquals(6291454, chunks[1].end)
        Assert.assertEquals(3145727, chunks[1].totalBytes)
    }
}
