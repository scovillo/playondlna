package io.github.scovillo.playondlna.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.scovillo.playondlna.model.VideoJobModel

@Composable
fun MainScreen(
    videoJobModel: VideoJobModel = viewModel(),
    onClearCache: () -> Unit,
    contentDlnaComposeView: @Composable () -> Unit
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
            composable("play") { PlayScreen(videoJobModel, onClearCache, contentDlnaComposeView) }
            composable("settings") { SettingsScreen(onClearCache) }
        }
    }
}