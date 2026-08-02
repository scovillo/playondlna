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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.scovillo.playondlna.R
import io.github.scovillo.playondlna.model.CacheControl
import io.github.scovillo.playondlna.model.VideoQuality
import io.github.scovillo.playondlna.model.VideoSettingsState
import io.github.scovillo.playondlna.upnpdlna.FavoriteDevices
import kotlinx.coroutines.flow.merge
import java.net.URL

@Composable
fun settingsScreen(
    videoSettingsState: VideoSettingsState,
    favoriteDevices: FavoriteDevices,
    cacheControl: CacheControl,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        merge(
            cacheControl.toastEvents,
            favoriteDevices.toastEvents,
        ).collect { event ->
            when (event) {
                is ToastEvent.Show ->
                    Toast.makeText(
                        context,
                        context.getString(event.messageResId),
                        Toast.LENGTH_LONG,
                    ).show()

                is ToastEvent.ShowPlain ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val deviceLocations by favoriteDevices.locations.collectAsState()
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
    ) {
        item { supportPlayOnDlna() }
        item { Spacer(Modifier.height(20.dp)) }
        item { videoQuality(videoSettingsState) }
        item { Spacer(Modifier.height(20.dp)) }
        item { subtitles(videoSettingsState) }
        item { Spacer(Modifier.height(20.dp)) }
        item { wlanProtection(videoSettingsState) }
        item { Spacer(Modifier.height(20.dp)) }
        item { customFavoriteDevices(favoriteDevices) }
        items(
            items = deviceLocations.toList(),
            key = { it },
        ) { device ->
            deviceItem(
                device = device,
                onDelete = { favoriteDevices.removeLocation(device) },
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
        item { clearCache(cacheControl) }
        item { Spacer(Modifier.height(20.dp)) }
        item { info(context) }
    }
}

@Composable
fun supportPlayOnDlna() {
    val context = LocalContext.current
    return Column {
        Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.support_desc_1))
        Text(stringResource(R.string.support_desc_2))
        Button(
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_github).toUri(),
                    )
                context.startActivity(intent)
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.star),
                tint = Color.Yellow,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(R.string.give_star))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.feedback_desc))
        Button(
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_discussions).toUri(),
                    )
                context.startActivity(intent)
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = stringResource(R.string.discussions),
                tint = Color.Green,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(R.string.join_discussions))
        }
        Button(
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_issues).toUri(),
                    )
                context.startActivity(intent)
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = stringResource(R.string.issue),
                tint = Color.Green,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(R.string.create_issue))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.financial_support_desc))
        Button(
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_sponsor).toUri(),
                    )
                context.startActivity(intent)
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEA4AAA),
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = stringResource(R.string.sponsor),
                tint = Color.White,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(R.string.become_sponsor))
        }
        Button(
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_paypal).toUri(),
                    )
                context.startActivity(intent)
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF003087),
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Coffee,
                contentDescription = stringResource(R.string.donate),
                tint = Color.White,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(R.string.buy_coffee))
        }
    }
}

@Composable
fun videoQuality(videoSettingsState: VideoSettingsState) {
    var expanded by remember { mutableStateOf(false) }
    return Column {
        Text(stringResource(R.string.video_quality_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.video_quality_desc))
        Button(
            onClick = { expanded = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.icon_color),
                    contentColor = colorResource(id = R.color.white),
                ),
        ) {
            Text(
                stringResource(
                    R.string.prefer_quality,
                    stringResource(videoSettingsState.videoQuality.value.titleRes),
                ),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            VideoQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    onClick = {
                        videoSettingsState.onVideoQualitySelect(quality)
                        expanded = false
                    },
                    text = { Text(stringResource(quality.titleRes)) },
                )
            }
        }
    }
}

@Composable
fun subtitles(videoSettingsState: VideoSettingsState) {
    val isSubtitleEnabled by videoSettingsState.isSubtitleEnabled
    val isInternalSubtitleEnabled by videoSettingsState.isInternalSubtitleEnabled
    Column {
        Text(stringResource(R.string.subtitles_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.subtitles_locale_label))
                Text(
                    stringResource(R.string.subtitles_external_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = isSubtitleEnabled,
                onCheckedChange = {
                    videoSettingsState.onSubtitleEnabledSelect(it)
                    if (!it) {
                        videoSettingsState.onSubtitleInternalEnabledSelect(false)
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.subtitles_internal_label))
                Text(
                    stringResource(R.string.subtitles_internal_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = isInternalSubtitleEnabled,
                onCheckedChange = {
                    videoSettingsState.onSubtitleInternalEnabledSelect(it)
                    if (it) {
                        videoSettingsState.onSubtitleEnabledSelect(true)
                    }
                },
            )
        }
        Spacer(Modifier.padding(bottom = 20.dp))
    }
}

@Composable
fun wlanProtection(videoSettingsState: VideoSettingsState) {
    val isEnabled by videoSettingsState.isWlanProtectionEnabled
    Column {
        Text(stringResource(R.string.wlan_protection_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.wlan_protection_label))
                Text(
                    stringResource(R.string.wlan_protection_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    videoSettingsState.onWlanProtectionEnabledSelect(it)
                },
            )
        }
        Spacer(Modifier.padding(bottom = 20.dp))
    }
}

@Composable
fun deviceItem(
    device: String,
    onDelete: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.StartToEnd) {
                    onDelete()
                    true
                } else {
                    false
                }
            },
        )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        },
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.device_icon))
                Spacer(Modifier.width(12.dp))
                Text(device)
            }
        }
    }
}

@Composable
fun customFavoriteDevices(favoriteDevices: FavoriteDevices) {
    var urlInput by remember { mutableStateOf("") }
    val isValidUrl =
        remember(urlInput) {
            try {
                val url = URL(urlInput)
                url.protocol == "http" || url.protocol == "https"
            } catch (_: Exception) {
                false
            }
        }
    Column {
        Text(
            stringResource(R.string.favorite_devices_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.favorite_devices_desc))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(stringResource(R.string.device_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = urlInput.isNotEmpty() && !isValidUrl,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                favoriteDevices.discoverLocation(URL(urlInput))
                urlInput = ""
            },
            enabled = isValidUrl,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF003087),
                    contentColor = Color.White,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_device_url))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun clearCache(cacheControl: CacheControl) {
    val sizeInGb by cacheControl.sizeInGb.collectAsState()
    return Column {
        Text(stringResource(R.string.cache_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.cache_usage))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("  ")
                    append(stringResource(R.string.cache_usage_value, sizeInGb))
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.cache_desc))
        Button(
            onClick = { cacheControl.clearCache() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.icon_color),
                    contentColor = colorResource(id = R.color.white),
                ),
        ) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = stringResource(R.string.clear_cache),
                tint = Color.White,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(id = R.string.clear_cache))
        }
    }
}

@Composable
fun info(context: Context) {
    return Column {
        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        Button(
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_releases).toUri(),
                    )
                context.startActivity(intent)
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.icon_color),
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
            )
            Spacer(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp))
            Text(stringResource(R.string.app_version, versionName ?: ""))
        }
    }
}
