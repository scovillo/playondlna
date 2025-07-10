package com.example.upnpdlna

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
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
import java.util.concurrent.Executors
import kotlin.math.roundToInt

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

    private var currentVideo: VideoFile? = null

    val listAdapter = DeviceListAdapter(
        mutableListOf()
    ) { item: DeviceDisplay ->
        if (currentVideo == null) {
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

        val sendIntent = intent
        if (sendIntent.action == Intent.ACTION_SEND) {
            if (sendIntent.type == "text/plain") {
                val url = sendIntent.extras?.getString("android.intent.extra.TEXT")
                if (url != null) {
                    val srcTextView = findViewById<TextView>(R.id.src)
                    srcTextView.text = url
                    this.startPlayback(url)
                }
            }
        }
    }

    fun startPlayback(url: String) {
        executorService.execute {
            Log.i("YoutubeDL", "Requesting: $url")
            val statusTextView = findViewById<TextView>(R.id.status)
            try {
                statusTextView.setText(R.string.preparing)
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                val request = YoutubeDLRequest(url)
                val rootDir = this.getExternalFilesDir(null)
                request.addOption(
                    "-o",
                    "${rootDir}/%(title)s$videoFileNameSeparator%(id)s$videoFileNameSeparator%(uploader)s"
                )
                request.addOption(
                    "-f",
                    "bestvideo[height>=480][height<=720]+bestaudio/best[height>=480][height<=720]"
                )
                request.addOption("--merge-output-format", "mp4")
                val progressBar = findViewById<ProgressBar>(R.id.progressBar)
                val extractor = YoutubeDlFileName("${rootDir!!.path}/", ".mp4")
                var fileName: String? = null
                YoutubeDL.getInstance()
                    .execute(request, null, fun(percentage: Float, status: Long, message: String) {
                        val progress = if (percentage > 0) percentage else 0.0f
                        progressBar.progress = progress.roundToInt()
                        if (fileName == null) {
                            fileName = extractor.extract(message)
                        }
                        Log.i("YoutubeDL", "$percentage%, Status $status, $message")
                    })
                progressBar.progress = 100
                Log.i("YoutubeDL", "Final filename: $fileName")
                currentVideo = VideoFile(fileName!!)
                statusTextView.setText(R.string.ready)
            } catch (e: YoutubeDLException) {
                statusTextView.setText(R.string.ready)
                e.printStackTrace()
            }
        }
    }

    fun play(device: Device<*, *, *>) {
        println("Video available under: ${currentVideo!!.url}")
        val avTransportService = device.services.find {
            it.serviceId.toString().contains("AVTransport")
        }
        val setAVTransportURIAction: ActionCallback = KodiSetAVTransportURI(
            avTransportService,
            currentVideo!!.url,
            currentVideo!!.metaData
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
}