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

package io.github.scovillo.playondlna.preparation

import io.github.scovillo.playondlna.AppLog
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale


val AudioStream.isOriginal: Boolean
    get() {
        return audioTrackType == AudioTrackType.ORIGINAL
    }
val AudioStream.hasBestCompatibility: Boolean
    get() {
        return format?.mimeType?.startsWith("audio/mp4") == true && codec?.startsWith("mp4a") == true
    }

fun List<AudioStream>.originals(): List<AudioStream> = filter { it.isOriginal }
fun List<AudioStream>.withBestCompatibility(): List<AudioStream> =
    this.filter { it.hasBestCompatibility }

fun List<AudioStream>.preferringLocale(locale: Locale): AudioStream =
    (filter { it.audioLocale?.language?.startsWith(locale.language) == true }
        .maxByOrNull { it.averageBitrate }
        ?: maxBy { it.averageBitrate })

val AudioStream.info: String
    get() {
        return "audioTrackName=$audioTrackName, audioTrackType=$audioTrackType, mimeType=${format?.mimeType}, codec=$codec, averageBitrate=$averageBitrate, audioLocale=$audioLocale"
    }

class AudioStreamSelection(
    private val audioStreams: List<AudioStream>,
    private val locale: Locale = Locale.getDefault()
) {
    init {
        if (audioStreams.isEmpty()) {
            throw IllegalStateException("No audio stream found!")
        }
    }

    fun best(): AudioStream {
        AppLog.i(
            "[ALL] AudioStreams",
            audioStreams.joinToString(System.lineSeparator()) { it.info }
        )
        AppLog.i("AudioStream", "System language: ${locale.language}")
        val originals = this.audioStreams.originals()
        AppLog.i(
            "[ORIGINAL] AudioStreams",
            if (originals.isEmpty()) "Empty!" else originals.joinToString(System.lineSeparator()) { it.info }
        )
        if (originals.isNotEmpty()) {
            val bestCompatibilities = originals.withBestCompatibility()
            AppLog.i(
                "[ORIGINAL] Best compatible AudioStreams",
                if (bestCompatibilities.isEmpty()) "Empty!" else bestCompatibilities.joinToString(
                    System.lineSeparator()
                ) { it.info }
            )
            if (bestCompatibilities.isNotEmpty()) {
                val selected = bestCompatibilities.preferringLocale(locale)
                AppLog.i("[ORIGINAL] AudioStreams", "Best compatible selected: ${selected.info}")
                return selected
            }
            val selected = originals.preferringLocale(locale)
            AppLog.i("[ORIGINAL] AudioStreams", "Selected: ${selected.info}")
            return selected
        }

        val compatibleStreams = audioStreams.withBestCompatibility()
        AppLog.i(
            "[COMPATIBLE] AudioStreams",
            if (compatibleStreams.isEmpty()) "Empty!" else compatibleStreams.joinToString(System.lineSeparator()) {
                "${it.audioTrackName}, ${it.audioTrackType}, ${it.format?.mimeType}, ${it.codec}, ${it.bitrate}, ${it.audioLocale}"
            }
        )
        if (compatibleStreams.isNotEmpty()) {
            val selected = compatibleStreams.preferringLocale(locale)
            AppLog.i("[COMPATIBLE] AudioStreams", "Selected: ${selected.info}")
            return selected
        }

        val fallback = audioStreams.preferringLocale(locale)
        AppLog.i(
            "[FALLBACK] AudioStreams",
            fallback.info
        )
        return fallback
    }
}
