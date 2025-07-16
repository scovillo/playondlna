package de.scovillo.playondlna.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.scovillo.playondlna.upnp.DlnaDeviceDescription

@Composable
fun DlnaDeviceDetail(device: DlnaDeviceDescription, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text("Device details", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text("Name: ${device.friendlyName}", style = MaterialTheme.typography.bodyLarge)
        Text("Modell: ${device.modelName}", style = MaterialTheme.typography.bodyMedium)
        Text("Hersteller: ${device.manufacturer}", style = MaterialTheme.typography.bodyMedium)
        Text("Typ: ${device.deviceType}", style = MaterialTheme.typography.bodySmall)
        Text("Location: ${device.location}", style = MaterialTheme.typography.bodySmall)
        Text("USN: ${device.usn}", style = MaterialTheme.typography.bodySmall)
    }
}
