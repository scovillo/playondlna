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

package io.github.scovillo.playondlna

enum class PlayOnDlnaLogStream {
    Console, Android
}

private interface PlayOnDlnaLogBridge {

    fun v(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)

    fun wtf(tag: String, msg: String, throwable: Throwable? = null)
}

private object ConsoleLogBridge : PlayOnDlnaLogBridge {

    override fun v(tag: String, msg: String) = println("VERBOSE: $tag: $msg")
    override fun d(tag: String, msg: String) = println("DEBUG: $tag: $msg")
    override fun i(tag: String, msg: String) = println("INFO: $tag: $msg")
    override fun w(tag: String, msg: String) = println("WARN: $tag: $msg")

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        println("ERROR: $tag: $msg")
        throwable?.printStackTrace()
    }

    override fun wtf(tag: String, msg: String, throwable: Throwable?) {
        println("WTF: $tag: $msg")
        throwable?.printStackTrace()
    }
}

private object AndroidLogBridge : PlayOnDlnaLogBridge {

    override fun v(tag: String, msg: String) {
        android.util.Log.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        android.util.Log.e(tag, msg, throwable)
    }

    override fun wtf(tag: String, msg: String, throwable: Throwable?) {
        android.util.Log.wtf(tag, msg, throwable)
    }
}

object AppLog : PlayOnDlnaLogBridge {
    private var stream = PlayOnDlnaLogStream.Android
    private val bridge: PlayOnDlnaLogBridge
        get() = if (stream == PlayOnDlnaLogStream.Console) ConsoleLogBridge else AndroidLogBridge

    fun setStream(value: PlayOnDlnaLogStream) {
        stream = value
    }

    override fun v(tag: String, msg: String) {
        bridge.v(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        bridge.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        bridge.i(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        bridge.w(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        bridge.e(tag, msg, throwable)
    }

    override fun wtf(tag: String, msg: String, throwable: Throwable?) {
        bridge.wtf(tag, msg, throwable)
    }
}