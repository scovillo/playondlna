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

package io.github.scovillo.playondlna.dlna

import kotlinx.serialization.Serializable

@Serializable
data class DlnaDevice(
    val usn: String,
    val st: String,
    val location: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val deviceType: String,
    val avTransportUrl: String?,
    val renderingControlUrl: String?,
    val services: List<DlnaService> = emptyList(),
) {
    fun requireAvTransportUrl(): String = requireNotNull(avTransportUrl) { "No AVTransport URL for $friendlyName" }
}

@Serializable
data class DlnaService(
    val serviceType: String,
    val serviceId: String?,
    val scpdUrl: String?,
    val controlUrl: String?,
    val eventSubUrl: String?,
)
