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

import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.PlayOnDlnaLogStream
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

class AudioStreamSelectionTest {

    @Before
    fun setup() {
        AppLog.setStream(PlayOnDlnaLogStream.Console)
    }

    @Test
    fun `Select original audio stream`() {
        val bestCompatibilityTag = ItagItem(1, ItagItem.ItagType.AUDIO, MediaFormat.M4A, "720p")
        bestCompatibilityTag.codec = "mp4a"
        val testAudioStreams = listOf(
            AudioStream.Builder().setId("Test de 1")
                .setAudioLocale(Locale("de"))
                .setAudioTrackName("Test de")
                .setAverageBitrate(1000)
                .setContent("Test de 1 content", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .build(),
            AudioStream.Builder().setId("Test en 1")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 1 en")
                .setAverageBitrate(1000)
                .setContent("Test en 1 content", false)
                .setMediaFormat(MediaFormat.M4A)
                .setItagItem(bestCompatibilityTag)
                .build(),
            AudioStream.Builder().setId("Test en 2")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 2 en")
                .setAverageBitrate(1000)
                .setContent("Test en 2 content", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setAudioTrackType(AudioTrackType.ORIGINAL)
                .build()
        )

        val classUnderTest = AudioStreamSelection(testAudioStreams, Locale("de"))

        assertEquals("Test en 2", classUnderTest.best().id)
    }

    @Test
    fun `Select best compatible original audio stream`() {
        val bestCompatibilityTag = ItagItem(1, ItagItem.ItagType.AUDIO, MediaFormat.M4A, "720p")
        bestCompatibilityTag.codec = "mp4a"
        val testAudioStreams = listOf(
            AudioStream.Builder().setId("Test de 1")
                .setAudioLocale(Locale("de"))
                .setAudioTrackName("Test de")
                .setAverageBitrate(1000)
                .setContent("Test de 1 content", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .build(),
            AudioStream.Builder().setId("Test en 1")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 1 en")
                .setAverageBitrate(1000)
                .setContent("Test en 1 content", false)
                .setMediaFormat(MediaFormat.M4A)
                .setItagItem(bestCompatibilityTag)
                .build(),
            AudioStream.Builder().setId("Test en 2")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 2 en")
                .setAverageBitrate(1000)
                .setContent("Test en 2 content", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setAudioTrackType(AudioTrackType.ORIGINAL)
                .build(),
            AudioStream.Builder().setId("Test de 2")
                .setAudioLocale(Locale("de"))
                .setAudioTrackName("Test 2 de")
                .setAverageBitrate(1000)
                .setContent("Test de 2 content", false)
                .setMediaFormat(MediaFormat.M4A)
                .setAudioTrackType(AudioTrackType.ORIGINAL)
                .build(),
            AudioStream.Builder().setId("Test de 3")
                .setAudioLocale(Locale("de"))
                .setAudioTrackName("Test 3 de")
                .setAverageBitrate(1000)
                .setContent("Test de 3 content", false)
                .setMediaFormat(MediaFormat.M4A)
                .setAudioTrackType(AudioTrackType.ORIGINAL)
                .setItagItem(bestCompatibilityTag)
                .build()
        )

        val classUnderTest = AudioStreamSelection(testAudioStreams, Locale("de"))

        assertEquals("Test de 3", classUnderTest.best().id)
    }

    @Test
    fun `Select best compatible stream on missing original audio stream`() {
        val bestCompatibilityTag = ItagItem(1, ItagItem.ItagType.AUDIO, MediaFormat.M4A, "720p")
        bestCompatibilityTag.codec = "mp4a"
        val testAudioStreams = listOf(
            AudioStream.Builder().setId("Test de 1")
                .setAudioLocale(Locale("de"))
                .setAudioTrackName("Test de")
                .setAverageBitrate(1000)
                .setContent("Test de 1 content", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .build(),
            AudioStream.Builder().setId("Test en 1")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 1 en")
                .setAverageBitrate(1000)
                .setContent("Test en 1 content", false)
                .setMediaFormat(MediaFormat.OPUS)
                .build(),
            AudioStream.Builder().setId("Test en 2")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 2 en")
                .setAverageBitrate(1000)
                .setContent("Test en 2 content", false)
                .setMediaFormat(MediaFormat.M4A)
                .setItagItem(bestCompatibilityTag)
                .build()
        )

        val classUnderTest = AudioStreamSelection(testAudioStreams, Locale("de"))

        assertEquals("Test en 2", classUnderTest.best().id)
    }

    @Test
    fun `Select first stream preferring locale on missing original and best compatibility audio stream`() {
        val testAudioStreams = listOf(
            AudioStream.Builder().setId("Test en 1")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 1 en")
                .setAverageBitrate(1000)
                .setContent("Test en 1 content", false)
                .setMediaFormat(MediaFormat.OPUS)
                .build(),
            AudioStream.Builder().setId("Test en 2")
                .setAudioLocale(Locale("en"))
                .setAudioTrackName("Test 2 en")
                .setAverageBitrate(1000)
                .setContent("Test en 2 content", false)
                .setMediaFormat(MediaFormat.M4A)
                .build(),
            AudioStream.Builder().setId("Test de 1")
                .setAudioLocale(Locale("de"))
                .setAudioTrackName("Test de")
                .setAverageBitrate(1000)
                .setContent("Test de 1 content", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .build(),
        )

        val classUnderTest = AudioStreamSelection(testAudioStreams, Locale("de"))

        assertEquals("Test de 1", classUnderTest.best().id)
    }
}