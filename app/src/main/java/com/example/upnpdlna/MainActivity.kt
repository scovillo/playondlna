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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.upnpdlna.ui.theme.UpnpDlnaTheme
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class BrowseRegistryListener(
    private val listAdapter: DeviceListAdapter,
    private val activity: MainActivity
) : DefaultRegistryListener() {
    /* Discovery performance optimization for very slow Android devices! */
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

    /* End of optimization, you can remove the whole block if your Android handset is fast (>= 600 Mhz) */
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

class KodiSetAVTransportURI(private val service: Service<*, *>?, url: String, metaData: String) :
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

    private var currentVideo: VideoInfo? = null

    val listAdapter = DeviceListAdapter(
        mutableListOf<DeviceDisplay>()
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

            // Clear the list
            listAdapter.clear()

            // Get ready for future device advertisements
            upnpService!!.get().registry.addListener(registryListener)

            // Now add all devices to the list we already know about
            for (device in upnpService!!.registry.devices.filter { it.type.type.contains("MediaRenderer") }) {
                registryListener!!.deviceAdded(device)
            }

            // Search asynchronously for all devices, they will respond soon
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
            getSystemService<NotificationManager?>(NotificationManager::class.java)
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
            try {
                val statusTextView = findViewById<TextView>(R.id.status)
                statusTextView.setText(R.string.collecting)
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                val videoInfo = YoutubeDL.getInstance().getInfo(url)
                statusTextView.setText(R.string.preparing)
                val request = YoutubeDLRequest(url)
                val rootDir = this.getExternalFilesDir(null)
                request.addOption("-o", "${rootDir}/%(title)s-%(id)s.%(ext)s")
                request.addOption(
                    "-f",
                    "bestvideo[height>=480][height<=720]+bestaudio/best[height>=480][height<=720]"
                )
                request.addOption("--merge-output-format", "mp4")
                val progressBar = findViewById<ProgressBar>(R.id.progressBar)
                YoutubeDL.getInstance()
                    .execute(request, null, fun(percentage: Float, status: Long, message: String) {
                        val progress = if (percentage > 0) percentage else 0.0f
                        progressBar.progress = progress.roundToInt()
                        Log.i("YoutubeDL", "$percentage%, Status $status, $message")
                    })
                progressBar.progress = 100
                statusTextView.setText(R.string.ready)
                currentVideo = videoInfo
            } catch (e: YoutubeDLException) {
                e.printStackTrace()
            }
        }
    }

    fun play(device: Device<*, *, *>) {
        val url = "http://${getLocalIpAddress()}:$serverPort/${currentVideo!!.id}"
        println("Video available under: $url")
        val avTransportService = device.services.find {
            it.serviceId.toString().contains("AVTransport")
        }
        val setAVTransportURIAction: ActionCallback = KodiSetAVTransportURI(
            avTransportService,
            url, """
                        <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                                   xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                                   xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                          <item id="${currentVideo!!.id}" parentID="0" restricted="1">
                            <dc:title>${currentVideo!!.title}</dc:title>
                            <dc:creator>${currentVideo!!.uploader}</dc:creator>
                            <upnp:class>object.item.videoItem</upnp:class>
                            <res protocolInfo="http-get:*:video/mp4:*">$url</res>
                          </item>
                        </DIDL-Lite>
                    """.trimIndent()
        )
        setAVTransportURIAction.setControlPoint(upnpService!!.controlPoint)
        setAVTransportURIAction.run()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (upnpService != null) {
            upnpService!!.registry.removeListener(registryListener)
        }
        // This will stop the UPnP service if nobody else is bound to it
        applicationContext.unbindService(serviceConnection)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UpnpDlnaTheme {
        Greeting("Android")
    }
}

fun getLocalIpAddress(): String? {
    try {
        val interfaces: Enumeration<NetworkInterface?> = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface: NetworkInterface? = interfaces.nextElement()

            // Nur aktive Interfaces, keine Loopbacks etc.
            if (!networkInterface!!.isUp() || networkInterface.isLoopback) {
                continue
            }

            val addresses: Enumeration<InetAddress?> = networkInterface.getInetAddresses()
            while (addresses.hasMoreElements()) {
                val inetAddress: InetAddress? = addresses.nextElement()

                // Nur IPv4 & keine Loopback-Adresse
                if (!inetAddress!!.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (e: SocketException) {
        e.printStackTrace()
    }
    return null
}