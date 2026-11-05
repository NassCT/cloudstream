package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.RepoPlugin
import com.lagradost.cloudstream4.desktop.Repositories
import com.lagradost.cloudstream4.desktop.SUGGESTED_REPOSITORIES
import com.lagradost.cloudstream4.desktop.SettingsStore
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.ic_baseline_add_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_autorenew_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_delete_outline_24
import com.lagradost.cloudstream4.desktop.res.netflix_download
import com.lagradost.cloudstream4.desktop.res.search_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pluginStatusLabel = mapOf(0 to "Down", 2 to "Slow", 3 to "Beta")

@Composable
fun ExtensionsScreen(app: AppState) {
    val settings by SettingsStore.state.collectAsState()
    val loadStates by PluginLoader.states.collectAsState()
    val scope = rememberCoroutineScope()

    var addOpen by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var available by remember { mutableStateOf<Loadable<List<RepoPlugin>>>(Loadable.Loading) }
    val busy = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(settings.repositories.map { it.url }, refreshKey) {
        available = Loadable.Loading
        available = runCatching {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    settings.repositories.map { repo ->
                        async { runCatching { Repositories.fetchPlugins(repo.url) }.getOrDefault(emptyList()) }
                    }.awaitAll().flatten()
                }
            }
        }.fold({ Loadable.Ok(it) }, { Loadable.Error(it.userMessage()) })
    }

    fun addRepo(input: String, onDone: () -> Unit = {}) {
        if (input.isBlank() || adding) return
        adding = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Repositories.add(input) } }
                .onSuccess { app.toast("Repository added: ${it.name}"); onDone() }
                .onFailure { app.toast("Couldn't add repository: ${it.userMessage()}") }
            adding = false
        }
    }

    fun install(rp: RepoPlugin) {
        val key = rp.plugin.internalName
        busy[key] = true
        scope.launch {
            runCatching { PluginLoader.install(rp) }
                .onSuccess {
                    when (it) {
                        is PluginLoader.LoadState.Loaded -> app.toast("${rp.plugin.name} installed")
                        is PluginLoader.LoadState.Failed -> app.toast("${rp.plugin.name} installed but failed to load: ${it.error}")
                    }
                }
                .onFailure { app.toast("Install failed: ${it.userMessage()}") }
            busy.remove(key)
        }
    }

    fun uninstall(internalName: String) {
        busy[internalName] = true
        scope.launch {
            runCatching { PluginLoader.uninstall(internalName) }
            busy.remove(internalName)
        }
    }

    val installed = settings.installedPlugins.associateBy { it.internalName }
    val all = (available as? Loadable.Ok)?.value.orEmpty().distinctBy { it.plugin.internalName }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extensions", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CsWhiteButton("Add repository", Res.drawable.ic_baseline_add_24) { addOpen = true }
            }
        }

        // Android's plugin stats bar: downloaded / not downloaded
        if (all.isNotEmpty()) item {
            val downloaded = all.count { it.plugin.internalName in installed }
            val failed = all.count { loadStates[it.plugin.internalName] is PluginLoader.LoadState.Failed }
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (downloaded - failed > 0) Box(Modifier.weight((downloaded - failed).toFloat()).height(10.dp).background(MaterialTheme.colorScheme.primary))
                    if (failed > 0) Box(Modifier.weight(failed.toFloat()).height(10.dp).background(MaterialTheme.colorScheme.error))
                    if (all.size - downloaded > 0) Box(Modifier.weight((all.size - downloaded).toFloat()).height(10.dp))
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, "Downloaded: ${downloaded - failed}")
                    if (failed > 0) LegendDot(MaterialTheme.colorScheme.error, "Failed: $failed")
                    LegendDot(MaterialTheme.colorScheme.surfaceVariant, "Not downloaded: ${all.size - downloaded}")
                }
            }
        }

        item { SectionTitle("Repositories") }
        items(settings.repositories, key = { "repo_" + it.url }) { repo ->
            ListCard {
                RemoteImage(repo.iconUrl?.replace("%size%", "128"), null, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f)) {
                    Text(repo.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(repo.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundIconButton(Res.drawable.ic_baseline_delete_outline_24, tint = MaterialTheme.colorScheme.onSurfaceVariant) { Repositories.remove(repo.url) }
            }
        }
        val suggestions = SUGGESTED_REPOSITORIES.filter { s -> settings.repositories.none { it.url == s.url } }
        items(suggestions, key = { "suggest_" + it.url }) { s ->
            ListCard {
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    s.description?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (adding) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else RoundIconButton(Res.drawable.ic_baseline_add_24, tint = MaterialTheme.colorScheme.onBackground) { addRepo(s.url) }
            }
        }
        if (settings.repositories.isEmpty() && suggestions.isEmpty()) item {
            Text("No repositories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Plugins", Modifier.weight(1f))
                Row(
                    Modifier.width(280.dp).height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CsIcon(Res.drawable.search_icon, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (filter.isEmpty()) Text("Filter", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        BasicTextField(
                            filter, { filter = it }, singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Res.drawable.ic_baseline_autorenew_24, tint = MaterialTheme.colorScheme.onBackground) { refreshKey++ }
            }
        }

        when (val a = available) {
            Loadable.Loading -> item { CenteredLoading(Modifier.fillMaxWidth().padding(24.dp)) }
            is Loadable.Error -> item { Text(a.message, color = MaterialTheme.colorScheme.error) }
            is Loadable.Ok -> {
                val list = all
                    .filter {
                        filter.isBlank() || it.plugin.name.contains(filter, true) ||
                            it.plugin.description?.contains(filter, true) == true ||
                            it.plugin.language?.contains(filter, true) == true
                    }
                    .sortedWith(compareBy({ it.plugin.internalName !in installed }, { it.plugin.name.lowercase() }))
                items(list, key = { "plugin_" + it.plugin.internalName }) { rp ->
                    val p = rp.plugin
                    val inst = installed[p.internalName]
                    val state = loadStates[p.internalName]
                    ListCard {
                        RemoteImage(p.iconUrl?.replace("%size%", "128"), null, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                pluginStatusLabel[p.status]?.let { Badge(it, MaterialTheme.colorScheme.error, Color.White) }
                            }
                            val info = listOfNotNull(
                                "v${p.version}",
                                p.language?.uppercase(),
                                p.tvTypes?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                                p.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "by $it" },
                            ).joinToString("  ·  ")
                            Text(info, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.description?.let { Text(it, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            when (state) {
                                is PluginLoader.LoadState.Failed -> Text("Failed to load: ${state.error}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                is PluginLoader.LoadState.Loaded -> if (inst != null && state.providers.isEmpty()) Text("Extractors only", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                null -> {}
                            }
                        }
                        when {
                            busy[p.internalName] == true -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            inst == null -> RoundIconButton(Res.drawable.netflix_download, tint = MaterialTheme.colorScheme.onBackground) { install(rp) }
                            else -> Row {
                                if (p.version > inst.version) RoundIconButton(Res.drawable.ic_baseline_autorenew_24, tint = MaterialTheme.colorScheme.primary) { install(rp) }
                                RoundIconButton(Res.drawable.ic_baseline_delete_outline_24, tint = MaterialTheme.colorScheme.onBackground) { uninstall(p.internalName) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Add repository", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("Repository URL or short code") }, modifier = Modifier.fillMaxWidth())
                    Text("Links like cloudstreamrepo://… and short codes from the Android app work too.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { addRepo(input) { addOpen = false } }, enabled = !adding && input.isNotBlank()) { Text(if (adding) "Adding…" else "Add") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 6.dp))
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** repository_item.xml-like row on a boxItemBackground card. */
@Composable
fun ListCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}
