package io.github.scovillo.playondlna.stream

data class Chunk(
    val start: Long,
    val end: Long,
    val totalBytes: Long
)

class ChunkCalculation(
    private val maxThreads: Int,
    private val minSizeInBytes: Long
) {
    private var currentThreads: Int = maxThreads

    fun chunks(totalContentLength: Long): List<Chunk> {
        adjustSettings(totalContentLength)

        val baseChunk = totalContentLength / currentThreads
        var remainder = totalContentLength % currentThreads

        val chunks = mutableListOf<Chunk>()
        var start = 0L

        for (i in 0 until currentThreads) {
            val size = if (remainder > 0) {
                remainder--
                baseChunk + 1
            } else {
                baseChunk
            }
            val end = start + size - 1
            chunks.add(Chunk(start, end, size))
            start = end + 1
        }

        return chunks
    }

    private fun adjustSettings(totalContentLength: Long) {
        currentThreads = maxThreads
        var sizePerThread = totalContentLength / currentThreads

        while (sizePerThread < minSizeInBytes && currentThreads > 1) {
            currentThreads--
            sizePerThread = totalContentLength / currentThreads
        }
    }
}
