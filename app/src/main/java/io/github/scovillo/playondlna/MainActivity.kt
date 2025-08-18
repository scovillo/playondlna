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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.arthenica.ffmpegkit.FFmpegKit
import io.github.scovillo.playondlna.model.DlnaListScreenModel
import io.github.scovillo.playondlna.model.VideoJobModel
import io.github.scovillo.playondlna.stream.OkHttpDownloader
import io.github.scovillo.playondlna.stream.WebServerService
import io.github.scovillo.playondlna.theme.PlayOnDlnaTheme
import io.github.scovillo.playondlna.ui.DlnaListScreen
import io.github.scovillo.playondlna.ui.MainScreen
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DlnaListScreenModel
    private val executorService = Executors.newCachedThreadPool()
    private val videoJobModel = VideoJobModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        executorService.execute {
            NewPipe.init(OkHttpDownloader())
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        "http_channel",
                        "HTTP Server",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            ContextCompat.startForegroundService(this, Intent(this, WebServerService::class.java))
        }
        viewModel = ViewModelProvider(this)[DlnaListScreenModel::class.java]
        viewModel.errorMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        setContent {
            PlayOnDlnaTheme {
                MainScreen(
                    videoJobModel,
                    onClearCache = { this.clearCache() }
                ) {
                    DlnaListScreen(videoJobModel)
                }
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    fun clearCache() {
        if (!cacheDir.exists())
            return
        val runningSessions = FFmpegKit.listSessions()
        val currentSession = this.videoJobModel.currentSession.value
        runningSessions.forEach {
            if (currentSession == null || it.sessionId != currentSession.sessionId) {
                Log.i("clearCache", "Cancel FFmpegKit with id ${it.sessionId}")
                FFmpegKit.cancel(it.sessionId)
            }
        }
        val currentVideoFile = this.videoJobModel.currentVideoFileInfo.value
        cacheDir.listFiles()?.forEach { file ->
            if (file.exists() && (currentVideoFile == null || !file.name.contains(currentVideoFile.id))) {
                file.delete()
            }
        }
        Toast.makeText(this, "Cache cleared!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val url = intent.extras?.getString("android.intent.extra.TEXT")
                if (url != null) {
                    this.videoJobModel.prepareVideo(url, cacheDir)
                }
            }
        }
    }
}