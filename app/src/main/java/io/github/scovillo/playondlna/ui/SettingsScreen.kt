package io.github.scovillo.playondlna.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.scovillo.playondlna.R

@Composable
fun SettingsScreen(onClearCache: () -> Unit) {
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
            Spacer(Modifier.height(16.dp))
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
            Spacer(Modifier.height(16.dp))
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
}
