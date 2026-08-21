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
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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

private val SectionSpacing = 24.dp
private val ContentSpacing = 16.dp
private val RelatedContentSpacing = 12.dp
private val ButtonSpacing = 16.dp

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
        item { Spacer(Modifier.height(SectionSpacing)) }
        item { videoQuality(videoSettingsState) }
        item { Spacer(Modifier.height(SectionSpacing)) }
        item { subtitles(videoSettingsState) }
        item { Spacer(Modifier.height(SectionSpacing)) }
        item { wlanProtection(videoSettingsState) }
        item { Spacer(Modifier.height(SectionSpacing)) }
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
        item { Spacer(Modifier.height(SectionSpacing)) }
        item { clearCache(cacheControl) }
        item { Spacer(Modifier.height(SectionSpacing)) }
        item { info(context) }
    }
}

@Composable
fun supportPlayOnDlna() {
    val context = LocalContext.current
    Column {
        Text(
            stringResource(R.string.support_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(ContentSpacing))
        Text(stringResource(R.string.support_desc_1))
        Spacer(Modifier.height(RelatedContentSpacing))
        Text(stringResource(R.string.support_desc_2))
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.Star,
            text = stringResource(R.string.give_star),
            color = Color.DarkGray,
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_github).toUri(),
                    )
                context.startActivity(intent)
            },
        )
        Spacer(Modifier.height(ContentSpacing))
        Text(stringResource(R.string.financial_support_desc))
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.Favorite,
            text = stringResource(R.string.support_on_liberapay),
            color = Color(0xFFF6C915),
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_liberapay).toUri(),
                    )
                context.startActivity(intent)
            },
        )
        Spacer(Modifier.height(ButtonSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.Coffee,
            text = stringResource(R.string.buy_coffee),
            color = Color(0xFF003087),
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_paypal).toUri(),
                    )
                context.startActivity(intent)
            },
        )
        Spacer(Modifier.height(ButtonSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.Favorite,
            text = stringResource(R.string.become_sponsor),
            color = Color(0xFFEA4AAA),
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_sponsor).toUri(),
                    )
                context.startActivity(intent)
            },
        )
        Spacer(Modifier.height(ContentSpacing))
        Text(stringResource(R.string.feedback_desc))
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.AutoMirrored.Filled.Chat,
            text = stringResource(R.string.join_discussions),
            color = Color.DarkGray,
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_discussions).toUri(),
                    )
                context.startActivity(intent)
            },
        )
        Spacer(Modifier.height(ButtonSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.BugReport,
            text = stringResource(R.string.create_issue),
            color = Color.DarkGray,
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_issues).toUri(),
                    )
                context.startActivity(intent)
            },
        )
    }
}

@Composable
fun videoQuality(videoSettingsState: VideoSettingsState) {
    var expanded by remember { mutableStateOf(false) }
    return Column {
        Text(stringResource(R.string.video_quality_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(ContentSpacing))
        Text(stringResource(R.string.video_quality_desc))
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.HighQuality,
            text =
                stringResource(
                    R.string.prefer_quality,
                    stringResource(videoSettingsState.videoQuality.value.titleRes),
                ),
            color = colorResource(id = R.color.icon_color),
            onClick = { expanded = true },
        )
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
        Spacer(Modifier.height(ContentSpacing))
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
        Spacer(Modifier.height(ContentSpacing))
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
    }
}

@Composable
fun wlanProtection(videoSettingsState: VideoSettingsState) {
    val isEnabled by videoSettingsState.isWlanProtectionEnabled
    Column {
        Text(stringResource(R.string.wlan_protection_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(ContentSpacing))
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
        Spacer(Modifier.height(ContentSpacing))
        Text(stringResource(R.string.favorite_devices_desc))
        Spacer(Modifier.height(RelatedContentSpacing))
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(stringResource(R.string.device_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = urlInput.isNotEmpty() && !isValidUrl,
        )
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.Add,
            text = stringResource(R.string.add_device_url),
            color = Color(0xFF003087),
            onClick = {
                favoriteDevices.discoverLocation(URL(urlInput))
                urlInput = ""
            },
            enabled = isValidUrl,
        )
    }
}

@Composable
fun clearCache(cacheControl: CacheControl) {
    val sizeInGb by cacheControl.sizeInGb.collectAsState()
    var showClearLibraryDialog by remember { mutableStateOf(false) }

    Column {
        Text(stringResource(R.string.cache_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(ContentSpacing))
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.cache_usage))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("  ")
                    append(stringResource(R.string.cache_usage_value, sizeInGb))
                }
            },
        )
        Spacer(Modifier.height(ContentSpacing))
        Text(stringResource(R.string.cache_desc))
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.CleaningServices,
            text = stringResource(id = R.string.clear_cache),
            color = colorResource(id = R.color.icon_color),
            onClick = { showClearLibraryDialog = true },
        )
    }

    if (showClearLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showClearLibraryDialog = false },
            title = { Text(stringResource(R.string.clear_library_dialog_title)) },
            text = { Text(stringResource(R.string.clear_library_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLibraryDialog = false
                        cacheControl.clearCache()
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun info(context: Context) {
    return Column {
        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        Spacer(Modifier.height(ContentSpacing))
        PlayOnDlnaButton(
            icon = Icons.Default.Info,
            text = stringResource(R.string.app_version, versionName ?: ""),
            color = colorResource(id = R.color.icon_color),
            onClick = {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        context.getString(R.string.url_releases).toUri(),
                    )
                context.startActivity(intent)
            },
        )
    }
}
