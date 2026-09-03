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
import io.github.scovillo.playondlna.dlna.DlnaPlaylist
import io.github.scovillo.playondlna.dlna.FavoriteDevices
import io.github.scovillo.playondlna.download.OkHttpDownloadClient
import io.github.scovillo.playondlna.model.CacheControl
import io.github.scovillo.playondlna.model.DeviceDiscoveryModel
import io.github.scovillo.playondlna.model.DlnaDevicesListScreenModel
import io.github.scovillo.playondlna.model.LibraryItem
import io.github.scovillo.playondlna.model.LibraryViewModel
import io.github.scovillo.playondlna.model.PlaylistViewModel
import io.github.scovillo.playondlna.model.VideoSettingsState
import io.github.scovillo.playondlna.persistence.LibraryManager
import io.github.scovillo.playondlna.persistence.PlaylistManager
import io.github.scovillo.playondlna.persistence.SettingsRepository
import io.github.scovillo.playondlna.preparation.MediaModel
import io.github.scovillo.playondlna.preparation.WifiConnectionState
import io.github.scovillo.playondlna.server.MediaServerService
import io.github.scovillo.playondlna.server.localIpAddress
import io.github.scovillo.playondlna.server.serverPort
import io.github.scovillo.playondlna.ui.DlnaListScreen
import io.github.scovillo.playondlna.ui.LibraryScreen
import io.github.scovillo.playondlna.ui.PlaylistsScreen
import io.github.scovillo.playondlna.ui.SettingsScreen
import io.github.scovillo.playondlna.ui.mainScreen
import io.github.scovillo.playondlna.ui.playOnDlnaTheme
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class MainActivity : ComponentActivity() {
    private lateinit var mediaModel: MediaModel
    private var isPermissionRequestInProgress = false

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            AppLog.i("MediaServerService", "Notification permission granted: $it")
            isPermissionRequestInProgress = false
            startWebServerService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        NewPipe.init(OkHttpDownloadClient())

        val settingsRepository = SettingsRepository(this)
        val libraryManager = LibraryManager(cacheDir)
        val libraryViewModel = LibraryViewModel(libraryManager)
        val playlistManager = PlaylistManager(cacheDir)
        val playlistViewModel = PlaylistViewModel(playlistManager)
        mediaModel =
            MediaModel(
                settingsRepository,
                WifiConnectionState(
                    getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager,
                ),
                cacheDir,
                libraryManager,
                playlistManager,
            )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaModel.requestMediaServerEvents.collect {
                    AppLog.i("MediaServerService", "Media serving requested, ensuring server is running...")
                    startWebServerServiceWithNotificationPermission()
                }
            }
        }
        val videoSettingsState = VideoSettingsState(settingsRepository)
        val cacheControl =
            CacheControl(
                cacheDir,
                mediaModel.currentVideoFile,
                mediaModel.currentFfmpegSession,
                mediaModel.onLibraryChange,
            )
        val favoriteDevices = FavoriteDevices(settingsRepository)
        val dlnaDevicesListScreenModel =
            DlnaDevicesListScreenModel(
                ViewModelProvider(this)[DeviceDiscoveryModel::class.java],
                favoriteDevices,
            )
        setContent {
            playOnDlnaTheme {
                var selectedPlaylistVideoFiles by remember { mutableStateOf(emptyList<LibraryItem>()) }
                var selectedPlaylist by remember { mutableStateOf<DlnaPlaylist?>(null) }
                mainScreen(
                    playScreen = {
                        DlnaListScreen(
                            mediaModel,
                            dlnaDevicesListScreenModel,
                            playlistVideoFiles = selectedPlaylistVideoFiles,
                            playlist = selectedPlaylist,
                        )
                    },
                    libraryScreen = { navController ->
                        LibraryScreen(libraryViewModel, playlistViewModel, mediaModel, navController, {
                            selectedPlaylistVideoFiles = emptyList()
                            selectedPlaylist = null
                            dlnaDevicesListScreenModel.clearPlaylistPlaybackModes()
                            navController.navigate("play") {
                                launchSingleTop = true
                            }
                        }) { playlist, items ->
                            dlnaDevicesListScreenModel.clearPlaylistPlaybackModes()
                            selectedPlaylistVideoFiles = mediaModel.selectPlaylist(items).let { items }
                            selectedPlaylist =
                                DlnaPlaylist(
                                    playlist,
                                    items,
                                    "http://${localIpAddress.value()}:$serverPort",
                                )
                            navController.navigate("play") { launchSingleTop = true }
                        }
                    },
                    playlistsScreen = { navController ->
                        PlaylistsScreen(playlistViewModel, libraryViewModel, mediaModel, navController) { playlist, items ->
                            dlnaDevicesListScreenModel.clearPlaylistPlaybackModes()
                            selectedPlaylistVideoFiles = mediaModel.selectPlaylist(items).let { items }
                            selectedPlaylist =
                                DlnaPlaylist(
                                    playlist,
                                    items,
                                    "http://${localIpAddress.value()}:$serverPort",
                                )
                            navController.navigate("play") { launchSingleTop = true }
                        }
                    },
                    settingsScreen = {
                        SettingsScreen(
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

    private fun handleShareIntent(intent: Intent?) {
        AppLog.i("ShareIntent", "Received new ShareIntent: $intent")
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val url = intent.extras?.getString("android.intent.extra.TEXT")
                if (url != null) {
                    this.mediaModel.import(url)
                }
            }
        }
    }

    private fun startWebServerService() {
        ContextCompat.startForegroundService(this, Intent(this, MediaServerService::class.java))
    }

    private fun startWebServerServiceWithNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (isPermissionRequestInProgress) return
            isPermissionRequestInProgress = true
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startWebServerService()
    }
}
