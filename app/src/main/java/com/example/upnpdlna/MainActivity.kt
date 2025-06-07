package com.example.upnpdlna

import android.app.ListActivity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.upnpdlna.ui.theme.UpnpDlnaTheme
import org.jupnp.android.AndroidUpnpService
import org.jupnp.android.AndroidUpnpServiceImpl
import org.jupnp.model.meta.Device
import org.jupnp.model.meta.LocalDevice
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import java.io.BufferedReader
import java.io.InputStreamReader


class BrowserActivity : ListActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // menu.add(0, 0, 0, R.string.searchLAN).setIcon(R.drawable.ic_menu_search)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /*
        when (item.itemId) {
            0 -> {
                if (upnpService == null) {
                    break
                }
                Toast.makeText(this, R.string.searchingLAN, Toast.LENGTH_SHORT).show()
                upnpService.getRegistry().removeAllRemoteDevices()
                upnpService.getControlPoint().search()
            }
        }
        return false
    } // ...

         */
        return true
    }
}

class DeviceDisplay(var device: Device<*, *, *>) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DeviceDisplay
        return device == that.device
    }

    override fun hashCode(): Int {
        return device.hashCode()
    }

    override fun toString(): String {
        val name =
            if (device.details != null && device.details.friendlyName != null)
                device.details.friendlyName
            else
                device.displayString
        // Display a little star while the device is being loaded (see performance optimization earlier)
        return if (device.isFullyHydrated) name else "$name *"
    }
}

class BrowseRegistryListener(private val listAdapter: ArrayAdapter<DeviceDisplay>?, private val activity: MainActivity) : DefaultRegistryListener() {
    /* Discovery performance optimization for very slow Android devices! */
    override fun remoteDeviceDiscoveryStarted(registry: Registry, device: RemoteDevice) {
        deviceAdded(device)
    }

    override fun remoteDeviceDiscoveryFailed(
        registry: Registry, device: RemoteDevice,
        ex: java.lang.Exception?
    ) {
        /*
        runOnUiThread(Runnable {
            Toast.makeText(
                this@BrowserActivity,
                ("Discovery failed of '" + device.displayString + "': "
                        + (ex?.toString() ?: "Couldn't retrieve device/service descriptors")),
                Toast.LENGTH_LONG
            ).show()
        })
        deviceRemoved(device)

         */
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
        println("Add device: ${device.displayString}")
        val d = DeviceDisplay(device)
        this.activity.runOnUiThread {
            val position: Int = listAdapter!!.getPosition(d)
            if (position >= 0) {
                // Device already in the list, re-set new value at same position
                listAdapter.remove(d)
                listAdapter.insert(d, position)
            } else {
                listAdapter.add(d)
            }
        }
    }

    fun deviceRemoved(device: Device<*, *, *>) {
        val d = DeviceDisplay(device)
        this.activity.runOnUiThread {
            listAdapter?.remove(d)
        }
    }
}

class MainActivity : ComponentActivity() {

    private var listAdapter: ArrayAdapter<DeviceDisplay>? = null

    private var registryListener: BrowseRegistryListener? = null

    private var upnpService: AndroidUpnpService? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            upnpService = service as AndroidUpnpService
            upnpService!!.get().startup()

            // Clear the list
            listAdapter!!.clear()

            // Get ready for future device advertisements
            upnpService!!.get().registry.addListener(registryListener)

            // Now add all devices to the list we already know about
            for (device in upnpService!!.registry.devices) {
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
        listAdapter = ArrayAdapter<DeviceDisplay>(this, R.layout.main_layout, R.id.devices)
        registryListener = BrowseRegistryListener(listAdapter, this)
        applicationContext.bindService(
            Intent(this, AndroidUpnpServiceImpl::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (upnpService != null) {
            upnpService!!.registry.removeListener(registryListener)
        }
        // This will stop the UPnP service if nobody else is bound to it
        applicationContext.unbindService(serviceConnection)
    }

    fun test(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        /*
        setContent {
            UpnpDlnaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

         */
        val youtubeUrl = "https://www.youtube.com/watch?v=1LnZEGttZeE"
        try {
            val process = ProcessBuilder("yt-dlp", "-g", youtubeUrl)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(
                InputStreamReader(process.inputStream)
            )
            val streamUrl = reader.readLine()
            Log.i("yt-dlp", "Stream URL: $streamUrl")

            // Hier kannst du die Stream-URL an den DLNA-Renderer senden
        } catch (e: Exception) {
            e.printStackTrace()
        }
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