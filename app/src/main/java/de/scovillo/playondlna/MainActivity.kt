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
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.jupnp.android.AndroidUpnpService
import org.jupnp.android.AndroidUpnpServiceImpl
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.Device
import org.jupnp.model.meta.LocalDevice
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.Service
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import org.jupnp.support.avtransport.callback.SetAVTransportURI
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.io.File
import java.util.concurrent.Executors

class BrowseRegistryListener(
    private val listAdapter: DeviceListAdapter,
    private val activity: MainActivity
) : DefaultRegistryListener() {
    override fun remoteDeviceDiscoveryStarted(registry: Registry, device: RemoteDevice) {
        deviceAdded(device)
    }

    override fun remoteDeviceDiscoveryFailed(
        registry: Registry, device: RemoteDevice,
        ex: Exception?
    ) {
        this.activity.runOnUiThread(Runnable {
            Toast.makeText(
                this.activity,
                ("Discovery failed of '" + device.displayString + "': "
                        + (ex?.toString() ?: "Couldn't retrieve device/service descriptors")),
                Toast.LENGTH_LONG
            ).show()
        })
        deviceRemoved(device)
    }

    override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
        deviceAdded(device)
    }

    override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
        deviceRemoved(device)
    }

    override fun localDeviceAdded(registry: Registry, device: LocalDevice) {
        deviceAdded(device)
    }

    override fun localDeviceRemoved(registry: Registry, device: LocalDevice) {
        deviceRemoved(device)
    }

    fun deviceAdded(device: Device<*, *, *>) {
        if (!device.type.type.contains("MediaRenderer")) {
            return
        }
        Log.i("BrowseRegistryListener", "Add device: ${device.displayString}")
        val deviceDisplay = DeviceDisplay(device)
        this.activity.runOnUiThread {
            listAdapter.add(deviceDisplay)
        }
    }

    fun deviceRemoved(device: Device<*, *, *>) {
        Log.i("BrowseRegistryListener", "Remove device: ${device.displayString}")
        val deviceDisplay = DeviceDisplay(device)
        this.activity.runOnUiThread {
            listAdapter.remove(deviceDisplay)
        }
    }
}

class KodiSetAVTransportURI(service: Service<*, *>?, url: String, metaData: String) :
    SetAVTransportURI(service, url, metaData) {
    override fun failure(
        invocation: ActionInvocation<*>?,
        operation: UpnpResponse?,
        defaultMsg: String?
    ) {
        TODO("Not yet implemented")
    }

}

class MainActivity : ComponentActivity() {

    private var registryListener: BrowseRegistryListener? = null

    private var upnpService: AndroidUpnpService? = null

    private val executorService = Executors.newFixedThreadPool(4)

    private var currentVideoFile: VideoFile? = null

    val listAdapter = DeviceListAdapter(
        mutableListOf()
    ) { item: DeviceDisplay ->
        if (currentVideoFile == null) {
            Log.d("Play", "No current video file!")
            return@DeviceListAdapter
        }
        this.play(item.device)
        Toast.makeText(
            this@MainActivity,
            "Auf $item gestartet",
            Toast.LENGTH_SHORT
        ).show()
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            upnpService = service as AndroidUpnpService
            upnpService!!.get().startup()
            listAdapter.clear()
            upnpService!!.get().registry.addListener(registryListener)
            for (device in upnpService!!.registry.devices.filter { it.type.type.contains("MediaRenderer") }) {
                registryListener!!.deviceAdded(device)
            }
            upnpService!!.get().controlPoint.search()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            upnpService?.get()?.shutdown()
            upnpService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)
        NewPipe.init(OkHttpDownloader())
        registryListener = BrowseRegistryListener(listAdapter, this)
        executorService.execute {
            applicationContext.bindService(
                Intent(this, AndroidUpnpServiceImpl::class.java),
                serviceConnection,
                BIND_AUTO_CREATE
            )
        }
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

        val recyclerView = findViewById<RecyclerView>(R.id.devices)
        recyclerView.setLayoutManager(LinearLayoutManager(this))

        recyclerView.setAdapter(listAdapter)

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    fun startPlayback(url: String) {
        executorService.execute {
            Log.i("YoutubeDL", "Requesting: $url")
            val statusTextView = findViewById<TextView>(R.id.status)
            try {
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                val bestVideo = extractor.videoStreams.maxByOrNull { it.height }
                val bestAudio = extractor.audioStreams.maxByOrNull { it.averageBitrate }
                if (bestVideo == null || bestAudio == null)
                    throw IllegalStateException("Streams nicht gefunden")
                val tempFile = File.createTempFile("muxed_", ".mp4", this.cacheDir)
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

                // 5. Muxing starten
                currentVideoFile = VideoFile(extractor)
                videoHttpServer.allFiles[currentVideoFile!!.id] = tempFile
                FFmpegKit.executeAsync(
                    ffmpegCmd.joinToString(" "),
                    { session ->
                        if (ReturnCode.isSuccess(session.returnCode)) {
                            statusTextView.setText(R.string.ready)
                            Log.d("Mux", "Muxing completed successfully")
                        } else {
                            statusTextView.setText(R.string.error)
                            Log.e("Mux", "Muxing failed")
                        }
                    },
                    { log -> Log.d("Mux", log.message) },
                    { stats -> Log.d("Mux", "Stats: ${stats.size}") }
                )
            } catch (e: Exception) {
                statusTextView.setText(R.string.error)
                e.printStackTrace()
            }
        }
    }

    fun play(device: Device<*, *, *>) {
        println("Video available under: ${currentVideoFile!!.url}")
        val avTransportService = device.services.find {
            it.serviceId.toString().contains("AVTransport")
        }
        val setAVTransportURIAction: ActionCallback = KodiSetAVTransportURI(
            avTransportService,
            currentVideoFile!!.url,
            currentVideoFile!!.metaData
        )
        setAVTransportURIAction.setControlPoint(upnpService!!.controlPoint)
        setAVTransportURIAction.run()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (upnpService != null) {
            upnpService!!.registry.removeListener(registryListener)
        }
        applicationContext.unbindService(serviceConnection)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val url = intent.extras?.getString("android.intent.extra.TEXT")
                if (url != null) {
                    val srcTextView = findViewById<TextView>(R.id.src)
                    srcTextView.text = url
                    this.startPlayback(url)
                }
            }
        }
    }
}