package io.github.scovillo.playondlna.model

import org.junit.Assert
import org.junit.Test


class VideoJobStateTest {
    @Test
    fun preparing() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.preparing()

        Assert.assertEquals(classUnderTest.status.value, VideoJobStatus.PREPARING)
        Assert.assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun playable() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.playable()

        Assert.assertEquals(classUnderTest.status.value, VideoJobStatus.PLAYABLE)
        Assert.assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun finalizing() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.finalizing()

        Assert.assertEquals(classUnderTest.status.value, VideoJobStatus.FINALIZING)
        Assert.assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun ready() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.ready()

        Assert.assertEquals(classUnderTest.status.value, VideoJobStatus.READY)
        Assert.assertEquals(classUnderTest.progress.value, 100.0f)
    }

    @Test
    fun error() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.error()

        Assert.assertEquals(classUnderTest.status.value, VideoJobStatus.ERROR)
        Assert.assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun updateProgress() {
        val classUnderTest = VideoJobState()
        Assert.assertEquals(classUnderTest.progress.value, 0.0f)

        classUnderTest.updateProgress(25f)

        Assert.assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun progressCanNotBeNegative() {
        val classUnderTest = VideoJobState()

        classUnderTest.updateProgress(-1f)

        Assert.assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun progressCanNotBeGreaterThan100() {
        val classUnderTest = VideoJobState()

        classUnderTest.updateProgress(101f)

        Assert.assertEquals(classUnderTest.progress.value, 100.0f)
    }
}