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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.scovillo.playondlna.download.OkHttpDownloadClient
import io.github.scovillo.playondlna.model.CacheControl
import io.github.scovillo.playondlna.model.DlnaDevicesListScreenModel
import io.github.scovillo.playondlna.model.LibraryViewModel
import io.github.scovillo.playondlna.model.PlaylistViewModel
import io.github.scovillo.playondlna.model.VideoSettingsState
import io.github.scovillo.playondlna.persistence.LibraryManager
import io.github.scovillo.playondlna.persistence.PlaylistManager
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.preparation.VideoJobModel
import io.github.scovillo.playondlna.preparation.WifiConnectionState
import io.github.scovillo.playondlna.server.VideoFile
import io.github.scovillo.playondlna.server.WebServerService
import io.github.scovillo.playondlna.server.createPlaylistM3u
import io.github.scovillo.playondlna.server.getLocalIpAddress
import io.github.scovillo.playondlna.server.serverPort
import io.github.scovillo.playondlna.server.videoHttpServer
import io.github.scovillo.playondlna.ui.dlnaListScreen
import io.github.scovillo.playondlna.ui.libraryScreen
import io.github.scovillo.playondlna.ui.mainScreen
import io.github.scovillo.playondlna.ui.playOnDlnaTheme
import io.github.scovillo.playondlna.ui.playlistsScreen
import io.github.scovillo.playondlna.ui.settingsScreen
import io.github.scovillo.playondlna.upnpdlna.DlnaMedia
import io.github.scovillo.playondlna.upnpdlna.FavoriteDevices
import io.github.scovillo.playondlna.upnpdlna.SsdpDevices
import io.github.scovillo.playondlna.upnpdlna.playlistMedia
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class MainActivity : ComponentActivity() {
    private lateinit var videoJobModel: VideoJobModel

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ ->
            startWebServerService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        NewPipe.init(OkHttpDownloadClient())
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        startWebServerService()

        val settingsRepository = SettingsRepository(this)
        val libraryManager = LibraryManager(cacheDir)
        val libraryViewModel = LibraryViewModel(libraryManager)
        val playlistManager = PlaylistManager(cacheDir)
        val playlistViewModel = PlaylistViewModel(playlistManager)
        videoJobModel =
            VideoJobModel(
                settingsRepository,
                WifiConnectionState(
                    getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager,
                ),
                cacheDir,
                libraryManager,
                playlistManager,
            )
        videoHttpServer.playlistProvider = playlistProvider@{ id, baseUrl, startIndex ->
            val playlist = playlistManager.getPlaylists().find { it.id == id } ?: return@playlistProvider null
            val items = libraryManager.getLibraryItems()
            createPlaylistM3u(playlist, items, baseUrl, startIndex).also {
                if (it != null) videoJobModel.preparePlaylist(items.filter { item -> item.metadata.id in playlist.videoIds })
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                videoJobModel.playbackStarted.collect {
                    startWebServerService()
                }
            }
        }
        val videoSettingsState = VideoSettingsState(settingsRepository)
        val cacheControl =
            CacheControl(
                cacheDir,
                videoJobModel.currentVideoFile,
                videoJobModel.currentSession,
                videoJobModel.completedSessions,
            )
        val favoriteDevices = FavoriteDevices(settingsRepository)
        val dlnaDevicesListScreenModel =
            DlnaDevicesListScreenModel(
                ViewModelProvider(this)[SsdpDevices::class.java],
                favoriteDevices,
                settingsRepository,
            )
        setContent {
            playOnDlnaTheme {
                var selectedPlaylistVideoFiles by remember { mutableStateOf(emptyList<VideoFile>()) }
                var selectedPlaylistMedia by remember { mutableStateOf<DlnaMedia?>(null) }
                mainScreen(
                    playScreen = {
                        dlnaListScreen(
                            videoJobModel,
                            dlnaDevicesListScreenModel,
                            playlistVideoFiles = selectedPlaylistVideoFiles,
                            playlistMedia = selectedPlaylistMedia,
                        )
                    },
                    libraryScreen = { navController ->
                        libraryScreen(libraryViewModel, playlistViewModel, videoJobModel, navController, {
                            selectedPlaylistVideoFiles = emptyList()
                            selectedPlaylistMedia = null
                            navController.navigate("play") {
                                launchSingleTop = true
                            }
                        }) { playlist, items ->
                            selectedPlaylistVideoFiles = videoJobModel.preparePlaylist(items)
                            selectedPlaylistMedia =
                                playlistMedia(
                                    playlist.id,
                                    playlist.name,
                                    "http://${getLocalIpAddress()}:$serverPort",
                                )
                            navController.navigate("play") { launchSingleTop = true }
                        }
                    },
                    playlistsScreen = { navController ->
                        playlistsScreen(playlistViewModel, libraryViewModel, videoJobModel, navController) { playlist, items ->
                            selectedPlaylistVideoFiles = videoJobModel.preparePlaylist(items)
                            selectedPlaylistMedia = playlistMedia(playlist.id, playlist.name, "http://${getLocalIpAddress()}:$serverPort")
                            navController.navigate("play") { launchSingleTop = true }
                        }
                    },
                    settingsScreen = {
                        settingsScreen(
                            videoSettingsState,
                            favoriteDevices,
                            cacheControl,
                        )
                    },
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

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    "http_channel",
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
    }

    private fun startWebServerService() {
        ContextCompat.startForegroundService(this, Intent(this, WebServerService::class.java))
    }
}
