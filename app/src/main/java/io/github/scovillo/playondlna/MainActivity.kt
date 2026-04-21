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
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import io.github.scovillo.playondlna.download.OkHttpDownloadClient
import io.github.scovillo.playondlna.model.CacheControl
import io.github.scovillo.playondlna.model.DlnaDevicesListScreenModel
import io.github.scovillo.playondlna.model.VideoSettingsState
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.preparation.VideoJobModel
import io.github.scovillo.playondlna.preparation.WifiConnectionState
import io.github.scovillo.playondlna.server.WebServerService
import io.github.scovillo.playondlna.ui.DlnaListScreen
import io.github.scovillo.playondlna.ui.MainScreen
import io.github.scovillo.playondlna.ui.PlayOnDlnaTheme
import io.github.scovillo.playondlna.ui.PlayScreen
import io.github.scovillo.playondlna.ui.SettingsScreen
import io.github.scovillo.playondlna.upnpdlna.FavoriteDevices
import io.github.scovillo.playondlna.upnpdlna.SsdpDevices
import org.schabi.newpipe.extractor.NewPipe

class MainActivity : ComponentActivity() {
    private lateinit var videoJobModel: VideoJobModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        NewPipe.init(OkHttpDownloadClient())
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    "http_channel",
                    "HTTP Server",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        ContextCompat.startForegroundService(this, Intent(this, WebServerService::class.java))
        val settingsRepository = SettingsRepository(this)
        videoJobModel = VideoJobModel(
            settingsRepository,
            WifiConnectionState(getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager),
            cacheDir
        )
        val videoSettingsState = VideoSettingsState(settingsRepository)
        val cacheControl = CacheControl(
            cacheDir,
            videoJobModel.currentVideoFile,
            videoJobModel.currentSession,
            videoJobModel.completedSessions
        )
        val favoriteDevices = FavoriteDevices(settingsRepository)
        val dlnaDevicesListScreenModel = DlnaDevicesListScreenModel(
            ViewModelProvider(this)[SsdpDevices::class.java],
            favoriteDevices
        )
        setContent {
            PlayOnDlnaTheme {
                MainScreen(
                    playScreen = {
                        PlayScreen(videoJobModel) {
                            DlnaListScreen(
                                videoJobModel,
                                dlnaDevicesListScreenModel
                            )
                        }
                    },
                    settingsScreen = {
                        SettingsScreen(
                            videoSettingsState,
                            favoriteDevices,
                            cacheControl
                        )
                    }
                )
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val url = intent.extras?.getString("android.intent.extra.TEXT")
                if (url != null) {
                    this.videoJobModel.prepareVideo(url)
                }
            }
        }
    }
}