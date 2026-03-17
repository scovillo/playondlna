package io.github.scovillo.playondlna.theme

import android.app.Activity
import android.view.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val darkColorScheme = darkColorScheme(
    primary = Color.Red,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.DarkGray,
    onSurface = Color.White,
)

@Composable
fun PlayOnDlnaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val window = (view.context as Activity).window
    window.decorView.setOnApplyWindowInsetsListener { view, insets ->
        val statusBarInsets = insets.getInsets(WindowInsets.Type.statusBars())
        view.setBackgroundColor(darkColorScheme.background.toArgb())
        view.setPadding(0, statusBarInsets.top, 0, 0)
        insets
    }
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}