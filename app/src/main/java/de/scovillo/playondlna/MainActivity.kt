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

package de.scovillo.playondlna

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import de.scovillo.playondlna.ui.DlnaListScreen
import de.scovillo.playondlna.ui.DlnaViewModel
import de.scovillo.playondlna.ui.MainScreen
import de.scovillo.playondlna.ui.PlayOnDlnaTheme
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DlnaViewModel

    private val executorService = Executors.newCachedThreadPool()

    private var currentVideoFile: VideoFile? = null
    private var currentSession: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)
        NewPipe.init(OkHttpDownloader())
        executorService.execute {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        "http_channel",
                        "HTTP Server",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            ContextCompat.startForegroundService(this, Intent(this, WebServerService::class.java))
        }
        viewModel = ViewModelProvider(this)[DlnaViewModel::class.java]
        setContent {
            PlayOnDlnaTheme {
                MainScreen(
                    srcText = stringResource(id = R.string.src_link),
                    statusText = stringResource(id = R.string.idle),
                    progress = 0, // z.B. dynamisch setzen
                    onClearCache = { /* Handle Clear Cache hier */ }
                ) {
                    DlnaListScreen()
                }

            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    fun onClearCache(view: View) {
        this.clearCache()
    }

    fun clearCache() {
        if (!cacheDir.exists())
            return
        val runningSessions = FFmpegKit.listSessions()
        runningSessions.forEach {
            if (currentSession == null || it.sessionId != currentSession!!.sessionId) {
                Log.i("clearCache", "Cancel FFmpegKit with id ${it.sessionId}")
                FFmpegKit.cancel(it.sessionId)
            }
        }
        cacheDir.listFiles()?.forEach { file ->
            if (file.exists() && (currentVideoFile == null || !file.name.contains(currentVideoFile!!.id))) {
                file.delete()
            }
        }
        Toast.makeText(this, "Cache cleared!", Toast.LENGTH_SHORT).show()
    }

    fun prepareVideo(url: String) {
        executorService.execute {
            Log.i("YoutubeDL", "Requesting: $url")
            val statusTextView = findViewById<TextView>(R.id.status)
            try {
                currentVideoFile = null
                currentSession = null
                statusTextView.setText(R.string.preparing)
                val progressBar = findViewById<ProgressBar>(R.id.progressBar)
                progressBar.progress = 0
                val srcTextView = findViewById<TextView>(R.id.src)
                srcTextView.text = url
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                srcTextView.text = extractor.name
                val bestVideo = extractor.videoStreams.maxByOrNull { it.height }
                val bestAudio = extractor.audioStreams.maxByOrNull { it.averageBitrate }
                if (bestVideo == null || bestAudio == null) {
                    statusTextView.setText(R.string.error)
                    throw IllegalStateException("Streams nicht gefunden")
                }
                val tempFile = File.createTempFile("${extractor.id}_muxed", ".mp4", this.cacheDir)
                val ffmpegCmd = listOf(
                    "-i", bestVideo.content,
                    "-i", bestAudio.content,
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-movflags +frag_keyframe+empty_moov+default_base_moof",
                    "-shortest",
                    "-y",
                    tempFile.absolutePath
                )
                videoHttpServer.allFiles[extractor.id] = tempFile
                currentSession = FFmpegKit.executeAsync(
                    ffmpegCmd.joinToString(" "),
                    { session ->
                        if (ReturnCode.isSuccess(session.returnCode)) {
                            progressBar.progress = 100
                            statusTextView.setText(R.string.ready)
                            Log.d("Mux", "Muxing completed successfully")
                        } else {
                            statusTextView.setText(R.string.error)
                            Log.e("Mux", "Muxing failed")
                        }
                    },
                    { log -> Log.d("Mux", log.message) },
                    { statistics ->
                        if (statistics.sessionId != currentSession?.sessionId) {
                            return@executeAsync
                        }
                        val videoDurationInMs = extractor.length * 1000
                        val progress = if (videoDurationInMs > 0) {
                            (statistics.time * 100 / videoDurationInMs).coerceIn(0.0, 100.0)
                        } else 0.0
                        progressBar.progress =
                            (progress * (100f / 3f)).roundToInt().coerceAtMost(100)
                        if (progressBar.progress == 100) {
                            currentVideoFile = VideoFile(extractor)
                            statusTextView.setText(R.string.ready)
                        }
                        Log.d("FFmpegProgress", "Fortschritt: $progress%")
                    }
                )
            } catch (e: Exception) {
                statusTextView.setText(R.string.error)
                e.printStackTrace()
            }
        }
    }

    fun play(device: Any) {
        println("Video available under: ${currentVideoFile!!.url}")
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val url = intent.extras?.getString("android.intent.extra.TEXT")
                if (url != null) {
                    this.prepareVideo(url)
                }
            }
        }
    }
}