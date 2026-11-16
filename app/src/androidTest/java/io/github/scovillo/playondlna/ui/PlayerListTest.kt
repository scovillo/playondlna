package io.github.scovillo.playondlna.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.dlna.DlnaDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerListTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun refreshAndScrollingSelectTheRequestedPlayer() {
        val devices =
            (1..20).map { index ->
                DlnaDevice(
                    usn = "player-$index",
                    st = "MediaRenderer",
                    location = "http://player$index/device.xml",
                    friendlyName = "Player $index",
                    manufacturer = "Test",
                    modelName = "Model",
                    deviceType = "MediaRenderer",
                    avTransportUrl = "http://player$index/control",
                    renderingControlUrl = null,
                )
            }
        var refreshLabel = ""
        var refreshes = 0
        val selected = mutableListOf<DlnaDevice>()
        compose.setContent {
            refreshLabel = LocalContext.current.getString(R.string.refresh)
            MaterialTheme {
                PlayerListContent(
                    devices = devices,
                    isLoading = false,
                    favorites = emptySet(),
                    selectedDevice = null,
                    modifier = Modifier.height(360.dp),
                    onRefresh = { refreshes++ },
                    onFavorite = {},
                    onSelected = { selected += it },
                )
            }
        }
        compose.onNodeWithContentDescription(refreshLabel).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText("Player 20"))
        compose.onNodeWithText("Player 20").performClick()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(listOf(devices.last()), selected)
        }
    }
}
