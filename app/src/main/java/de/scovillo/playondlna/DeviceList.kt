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

package de.scovillo.playondlna

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import org.jupnp.model.meta.Device


class DeviceDisplay(var device: Device<*, *, *>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as DeviceDisplay
        return device == that.device
    }

    override fun hashCode(): Int {
        return device.hashCode()
    }

    override fun toString(): String {
        val name =
            if (device.details != null && device.details.friendlyName != null)
                device.details.friendlyName
            else
                device.displayString
        return if (device.isFullyHydrated) name else "$name *"
    }
}

fun interface OnDeviceClickListener {
    fun onDeviceClick(item: DeviceDisplay)
}


class DeviceListAdapter(
    private val itemList: MutableList<DeviceDisplay>,
    private val listener: OnDeviceClickListener
) :
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder?>() {

    class DeviceViewHolder(itemView: View) : ViewHolder(itemView) {
        var textView: TextView = itemView.findViewById(R.id.textViewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_layout, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val item = itemList[position]
        holder.textView.text = item.toString()
        holder.textView.setOnClickListener { v -> listener.onDeviceClick(item) }
        val bgColorRes: Int = if (position % 2 == 0) R.color.dark_grey else R.color.very_dark_grey
        holder.itemView.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, bgColorRes)
        )
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun add(item: DeviceDisplay) {
        val position = itemList.indexOf(item)
        if (position > -1) {
            remove(position)
            itemList.add(position, item)
        } else {
            itemList.add(item)
        }
        notifyItemInserted(itemList.size - 1)
    }

    fun remove(item: DeviceDisplay) {
        val position = itemList.indexOf(item)
        return remove(position)
    }

    fun remove(position: Int) {
        if (position > -1) {
            itemList.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun clear() {
        itemList.forEach { remove(it) }
        itemList.clear()
    }
}