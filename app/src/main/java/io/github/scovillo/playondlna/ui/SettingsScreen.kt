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

package io.github.scovillo.playondlna.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.SettingsState
import io.github.scovillo.playondlna.model.VideoQuality

@Composable
fun SettingsScreen(state: SettingsState) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            SupportPlayOnDlna(context)
            Spacer(Modifier.height(16.dp))
            VideoQuality(state)
            Spacer(Modifier.height(16.dp))
            ClearCache(state.onClearCache)
            Spacer(Modifier.height(16.dp))
            Info(context)
        }
    }
}

@Composable
fun SupportPlayOnDlna(context: Context) {
    return Column {
        Text("\uD83C\uDF81 Support PlayOnDlna", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "This app is completely free and ad-free. If it serves you well and you'd like to give something back, " +
                    "you're welcome to use one of the following options."
        )
        Text(
            "The simplest way to support PlayOnDlna is by giving it a star on GitHub. " +
                    "Stars increase the project’s visibility within the GitHub community and can help attract more users and contributors."
        )
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/scovillo/playondlna".toUri()
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Star",
                tint = Color.Yellow
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text("Give a Star on GitHub")
        }
        Spacer(Modifier.height(12.dp))
        Text("You have a cool idea for the app, looking for troubleshooting or found something which is not working correctly?")
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/scovillo/playondlna/discussions".toUri()
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Discussions",
                tint = Color.Green
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text("Join discussions on Github")
        }
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/scovillo/playondlna/issues".toUri()
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Issue",
                tint = Color.Green
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text("Create an issue on Github")
        }
        Spacer(Modifier.height(12.dp))
        Text("You would like to go a step further and support financially?")
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/sponsors/scovillo".toUri()
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEA4AAA),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Sponsor",
                tint = Color.White
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text("Become sponsor on GitHub️")
        }
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://www.paypal.me/muemmelmaus".toUri()
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF003087),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Coffee,
                contentDescription = "Donate",
                tint = Color.White
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text("Buy me a coffee via PayPal")
        }
    }
}

@Composable
fun VideoQuality(settingsState: SettingsState) {
    var expanded by remember { mutableStateOf(false) }
    return Column {
        Text("\uD83D\uDCF9 Video Quality", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Choose your preferred video quality. Due to compatibility reasons, the finally chosen quality can be lower than your preferred.")
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.icon_color),
                contentColor = colorResource(id = R.color.white)
            )
        ) {
            Text("Prefer ${settingsState.videoQuality.value.title}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            VideoQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    onClick = {
                        settingsState.onVideoQualitySelect(quality)
                        expanded = false
                    },
                    text = { Text(quality.title) }
                )
            }
        }
    }
}

@Composable
fun ClearCache(onClearCache: () -> Unit) {
    return Column {
        Text("\uD83D\uDCBE Cache", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "PlayOnDlna is muxing audio and video streams to streamable video files for you, " +
                    "saving as temp files to provide them in your local network. " +
                    "This has the pleasant side effect that the videos are immediately available for streaming the next time. " +
                    "But over time, disk usage will increase and you can use the button below to clean up and free space."
        )
        Button(
            onClick = onClearCache,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.icon_color),
                contentColor = colorResource(id = R.color.white)
            )
        ) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = "Clear Cache",
                tint = Color.White
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(id = R.string.clear_cache))
        }
    }
}

@Composable
fun Info(context: Context) {
    return Column {
        Text("\uD83D\uDCA1 Info", style = MaterialTheme.typography.titleLarge)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/scovillo/playondlna/releases".toUri()
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.icon_color),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "App Version",
                tint = Color.White
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text("App Version: $versionName")
        }
    }
}