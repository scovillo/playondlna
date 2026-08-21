package io.github.scovillo.playondlna.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun mainScreen(
    playScreen: @Composable () -> Unit,
    libraryScreen: @Composable (NavHostController) -> Unit,
    playlistsScreen: @Composable (NavHostController) -> Unit,
    settingsScreen: @Composable () -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { playOnDlnaNavBar(navController = navController) },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "play",
            modifier = Modifier.padding(paddingValues),
        ) {
            composable("play") { playScreen() }
            composable("library") { libraryScreen(navController) }
            composable("playlists") { playlistsScreen(navController) }
            composable("playlist/{playlistId}") { playlistsScreen(navController) }
            composable("settings") { settingsScreen() }
        }
    }
}
