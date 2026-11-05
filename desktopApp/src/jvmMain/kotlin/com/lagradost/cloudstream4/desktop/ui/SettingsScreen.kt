package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.desktop.AppDirs
import com.lagradost.cloudstream4.desktop.DesktopNetwork
import com.lagradost.cloudstream4.desktop.DnsChoice
import com.lagradost.cloudstream4.desktop.ExternalPlayer
import com.lagradost.cloudstream4.desktop.PlayerChoice
import com.lagradost.cloudstream4.desktop.SettingsStore
import com.lagradost.cloudstream4.desktop.player.Vlc
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.ic_baseline_folder_open_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_ondemand_video_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_open_in_new_24
import com.lagradost.cloudstream4.desktop.res.ic_cloudstream_monochrome
import com.lagradost.cloudstream4.desktop.res.ic_github_logo
import com.lagradost.cloudstream4.desktop.res.ic_outline_subtitles_24
import org.jetbrains.compose.resources.DrawableResource
import java.awt.Desktop

@Composable
fun SettingsScreen() {
    val settings by SettingsStore.state.collectAsState()
    val vlc = ExternalPlayer.findVlc()
    val mpv = ExternalPlayer.findMpv()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Group("Player")
            SettingRow(
                Res.drawable.ic_baseline_ondemand_video_24, "Built-in player",
                if (Vlc.available) "Plays inside CloudStream (uses VLC's engine)" else "VLC isn't installed: install it from videolan.org to enable the built-in player"
            ) {
                Switch(
                    checked = settings.useBuiltInPlayer && Vlc.available,
                    enabled = Vlc.available,
                    onCheckedChange = { v -> SettingsStore.update { it.copy(useBuiltInPlayer = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
            SettingRow(Res.drawable.ic_outline_subtitles_24, "Auto-select subtitles", "Picks subtitles in your system language when a source has them") {
                Switch(
                    checked = settings.autoSelectSubtitles,
                    onCheckedChange = { v -> SettingsStore.update { it.copy(autoSelectSubtitles = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }

            Group("External player")
            Text(
                "Used by the \"open in external player\" button. mpv forwards every HTTP header some sources need; VLC only referer and user agent.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
                PlayerChoice.entries.forEach { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.player == choice,
                            onClick = { SettingsStore.update { it.copy(player = choice) } },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            when (choice) {
                                PlayerChoice.Auto -> "Automatic (mpv, then VLC)"
                                PlayerChoice.Vlc -> "VLC  ·  ${vlc ?: "not found"}"
                                PlayerChoice.Mpv -> "mpv  ·  ${mpv ?: "not found"}"
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = settings.vlcPath.orEmpty(),
                    onValueChange = { v -> SettingsStore.update { it.copy(vlcPath = v.ifBlank { null }) } },
                    label = { Text("Custom VLC path (vlc.exe)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                OutlinedTextField(
                    value = settings.mpvPath.orEmpty(),
                    onValueChange = { v -> SettingsStore.update { it.copy(mpvPath = v.ifBlank { null }) } },
                    label = { Text("Custom mpv path (mpv.exe)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }

            Group("Network")
            Text(
                "DNS over HTTPS gets around sites blocked by your internet provider's DNS.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
                DnsChoice.entries.forEach { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.dns == choice,
                            onClick = {
                                SettingsStore.update { it.copy(dns = choice) }
                                DesktopNetwork.install(choice)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(choice.label)
                    }
                }
            }

            Group("General")
            SettingRow(Res.drawable.ic_baseline_folder_open_24, "Data folder", AppDirs.root.absolutePath, onClick = {
                runCatching { Desktop.getDesktop().open(AppDirs.root) }
            })
            SettingRow(Res.drawable.ic_baseline_folder_open_24, "Log file", "Open it to report a problem", onClick = {
                runCatching { Desktop.getDesktop().open(com.lagradost.cloudstream4.desktop.AppLog.file) }
            })

            Group("About")
            SettingRow(Res.drawable.ic_cloudstream_monochrome, "CloudStream for Windows", "Unofficial desktop build of CloudStream (GPL-3.0). It doesn't host any content: everything comes from the extensions you install.")
            SettingRow(Res.drawable.ic_github_logo, "CloudStream on GitHub", "github.com/recloudstream/cloudstream", onClick = {
                openInBrowser("https://github.com/recloudstream/cloudstream")
            }) { CsIcon(Res.drawable.ic_baseline_open_in_new_24, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp, start = 4.dp))
}

/** Preference row like main_settings.xml: icon, title, summary, optional trailing widget. */
@Composable
private fun SettingRow(
    icon: DrawableResource,
    title: String,
    summary: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CsIcon(icon, size = 24.dp, tint = MaterialTheme.colorScheme.onBackground)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (summary != null) Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
