package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.desktop.AppDirs
import com.lagradost.cloudstream4.desktop.ExternalPlayer
import com.lagradost.cloudstream4.desktop.PlayerChoice
import com.lagradost.cloudstream4.desktop.SettingsStore
import java.awt.Desktop

@Composable
fun SettingsScreen() {
    val settings by SettingsStore.state.collectAsState()
    val vlc = ExternalPlayer.findVlc()
    val mpv = ExternalPlayer.findMpv()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 800.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Video player", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Videos open in an external player. mpv is recommended: it forwards every HTTP header some sources need. VLC only forwards referer and user agent.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PlayerChoice.entries.forEach { choice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.player == choice, onClick = { SettingsStore.update { it.copy(player = choice) } })
                Text(
                    when (choice) {
                        PlayerChoice.Auto -> "Automatic (mpv, then VLC)"
                        PlayerChoice.Vlc -> "VLC"
                        PlayerChoice.Mpv -> "mpv"
                    }
                )
            }
        }
        Text("Detected VLC: ${vlc ?: "not found"}", style = MaterialTheme.typography.bodySmall)
        Text("Detected mpv: ${mpv ?: "not found"}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = settings.vlcPath.orEmpty(),
            onValueChange = { v -> SettingsStore.update { it.copy(vlcPath = v.ifBlank { null }) } },
            label = { Text("Custom VLC path (vlc.exe)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = settings.mpvPath.orEmpty(),
            onValueChange = { v -> SettingsStore.update { it.copy(mpvPath = v.ifBlank { null }) } },
            label = { Text("Custom mpv path (mpv.exe)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Text("Data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(AppDirs.root.absolutePath, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(AppDirs.root) } }) { Text("Open data folder") }

        Text("About", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "CloudStream Plus is an unofficial Windows build of CloudStream (GPL-3.0). " +
                "It doesn't host or provide any content: everything comes from the extensions you install.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = { openInBrowser("https://github.com/recloudstream/cloudstream") }) { Text("CloudStream on GitHub") }
    }
}
