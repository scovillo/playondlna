/*
 * PlayOnDlna - An Android application to play media on dlna devices
 * Copyright (C) 2025 Lukas Scheerer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.scovillo.playondlna.model

import org.junit.Assert.assertEquals
import org.junit.Test


class VideoJobStateTest {
    @Test
    fun preparing() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.preparing()

        assertEquals(classUnderTest.status.value, VideoJobStatus.PREPARING)
        assertEquals(classUnderTest.progress.value, 0.0f)
    }


    @Test
    fun finalizing() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.finalizing()

        assertEquals(classUnderTest.status.value, VideoJobStatus.FINALIZING)
        assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun ready() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.ready()

        assertEquals(classUnderTest.status.value, VideoJobStatus.READY)
        assertEquals(classUnderTest.progress.value, 100.0f)
    }

    @Test
    fun error() {
        val classUnderTest = VideoJobState()
        classUnderTest.updateProgress(25f)

        classUnderTest.error()

        assertEquals(classUnderTest.status.value, VideoJobStatus.ERROR)
        assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun updateProgress() {
        val classUnderTest = VideoJobState()
        assertEquals(classUnderTest.progress.value, 0.0f)

        classUnderTest.updateProgress(25f)

        assertEquals(classUnderTest.progress.value, 25.0f)
    }

    @Test
    fun progressCanNotBeNegative() {
        val classUnderTest = VideoJobState()

        classUnderTest.updateProgress(-1f)

        assertEquals(classUnderTest.progress.value, 0.0f)
    }

    @Test
    fun progressCanNotBeGreaterThan100() {
        val classUnderTest = VideoJobState()

        classUnderTest.updateProgress(101f)

        assertEquals(classUnderTest.progress.value, 100.0f)
    }
}