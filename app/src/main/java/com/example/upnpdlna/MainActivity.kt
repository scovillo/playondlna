package com.example.upnpdlna

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.upnpdlna.ui.theme.UpnpDlnaTheme
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.model.meta.Device
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import java.io.BufferedReader
import java.io.InputStreamReader


class MainActivity : ComponentActivity() {

    private var upnpService: UpnpService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        upnpService = UpnpServiceImpl()
        upnpService!!.startup()
        upnpService!!.registry.addListener(createRegistryListener())
        upnpService!!.controlPoint.search()
    }

    override fun onDestroy() {
        super.onDestroy()
        upnpService?.shutdown()
    }

    private fun createRegistryListener(): DefaultRegistryListener {
        return object : DefaultRegistryListener() {
            override fun deviceAdded(registry: Registry?, device: Device<*, *, *>) {
                Log.i("UPNP", "Gefundenes Gerät: " + device.getDisplayString())
            }

            override fun deviceRemoved(registry: Registry?, device: Device<*, *, *>) {
                Log.i("UPNP", "Entferntes Gerät: " + device.getDisplayString())
            }
        }
    }

    fun test(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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