package io.github.scovillo.playondlna.preparation

import org.junit.Assert
import org.junit.Test

class VideoJobStateTest {
    @Test
    fun preparing() {
        val classUnderTest = MediaFileJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.preparing()

        Assert.assertEquals(classUnderTest.status.value, MediaFileJobStatus.PREPARING)
        Assert.assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun finalizing() {
        val classUnderTest = MediaFileJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.finalizing()

        Assert.assertEquals(classUnderTest.status.value, MediaFileJobStatus.FINALIZING)
        Assert.assertEquals(classUnderTest.progress.value, 50.0f)
    }

    @Test
    fun ready() {
        val classUnderTest = MediaFileJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.ready()

        Assert.assertEquals(classUnderTest.status.value, MediaFileJobStatus.READY)
        Assert.assertEquals(classUnderTest.progress.value, 100.0f)
    }

    @Test
    fun idleResetsProgress() {
        val classUnderTest = MediaFileJobState()
        classUnderTest.updateProgress(100f)

        classUnderTest.idle()

        Assert.assertEquals(classUnderTest.status.value, MediaFileJobStatus.IDLE)
        Assert.assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun error() {
        val classUnderTest = MediaFileJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.error()

        Assert.assertEquals(classUnderTest.status.value, MediaFileJobStatus.ERROR)
        Assert.assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun updateProgress() {
        val classUnderTest = MediaFileJobState()
        Assert.assertEquals(classUnderTest.progress.value, 0.0f)

        classUnderTest.updateProgress(25f)

        Assert.assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun mapsDownloadAndFinalizingProgressToHalves() {
        val classUnderTest = MediaFileJobState()

        classUnderTest.updateDownloadProgress(100f)
        Assert.assertEquals(classUnderTest.progress.value, 50.0f)

        classUnderTest.updateFinalizingProgress(50f)
        Assert.assertEquals(classUnderTest.progress.value, 75.0f)
    }

    @Test
    fun progressCanNotBeNegative() {
        val classUnderTest = MediaFileJobState()

        classUnderTest.updateProgress(-1f)

        Assert.assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun progressCanNotBeGreaterThan100() {
        val classUnderTest = MediaFileJobState()

        classUnderTest.updateProgress(101f)

        Assert.assertEquals(classUnderTest.progress.value, 100.0f)
    }
}
