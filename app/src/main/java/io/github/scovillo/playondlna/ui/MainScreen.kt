package io.github.scovillo.playondlna.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen(
    playScreen: @Composable () -> Unit,
    settingsScreen: @Composable () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { PlayOnDlnaNavBar(navController = navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "play",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("play") { playScreen() }
            composable("settings") { settingsScreen() }
        }
    }
}