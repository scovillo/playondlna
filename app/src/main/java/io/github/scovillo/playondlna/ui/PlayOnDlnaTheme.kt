package io.github.scovillo.playondlna.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private val darkColorScheme =
    darkColorScheme(
        primary = Color.Red,
        onPrimary = Color.Black,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.DarkGray,
        onSurface = Color.White,
    )

@Composable
fun playOnDlnaTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val window = (view.context as Activity).window
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
        val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        view.setBackgroundColor(darkColorScheme.background.toArgb())
        view.setPadding(0, statusBarInsets.top, 0, 0)
        insets
    }
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content,
    )
}
