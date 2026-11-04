package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.RepoPlugin
import com.lagradost.cloudstream4.desktop.Repositories
import com.lagradost.cloudstream4.desktop.SUGGESTED_REPOSITORIES
import com.lagradost.cloudstream4.desktop.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pluginStatusLabel = mapOf(0 to "Down", 1 to "", 2 to "Slow", 3 to "Beta")

@Composable
fun ExtensionsScreen(app: AppState) {
    val settings by SettingsStore.state.collectAsState()
    val loadStates by PluginLoader.states.collectAsState()
    val scope = rememberCoroutineScope()

    var repoInput by remember { mutableStateOf("") }
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

    fun addRepo(input: String) {
        if (input.isBlank() || adding) return
        adding = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Repositories.add(input) } }
                .onSuccess { app.toast("Repository added: ${it.name}"); repoInput = "" }
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Extensions", style = MaterialTheme.typography.headlineSmall) }

        // ---- Repositories
        item { Text("Repositories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    singleLine = true,
                    placeholder = { Text("Repository URL (repo.json) or short code") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { addRepo(repoInput) }, enabled = !adding) {
                    if (adding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Add")
                }
            }
        }
        val suggestions = SUGGESTED_REPOSITORIES.filter { s -> settings.repositories.none { it.url == s.url } }
        items(suggestions, key = { "suggest_" + it.url }) { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Suggested: ${s.name}", style = MaterialTheme.typography.titleSmall)
                        s.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedButton(onClick = { addRepo(s.url) }, enabled = !adding) { Text("Add") }
                }
            }
        }
        items(settings.repositories, key = { "repo_" + it.url }) { repo ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteImage(repo.iconUrl?.replace("%size%", "128"), null, null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f)) {
                    Text(repo.name, style = MaterialTheme.typography.bodyLarge)
                    Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { Repositories.remove(repo.url) }) { Text("Remove") }
            }
        }

        // ---- Plugins
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Text("Available extensions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Filter") }, modifier = Modifier.width(260.dp)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { refreshKey++ }) { Text("Refresh") }
            }
        }

        when (val a = available) {
            Loadable.Loading -> item { CenteredLoading(Modifier.fillMaxWidth().padding(24.dp)) }
            is Loadable.Error -> item { Text(a.message, color = MaterialTheme.colorScheme.error) }
            is Loadable.Ok -> {
                if (settings.repositories.isEmpty()) item {
                    Text("Add a repository to see its extensions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val installed = settings.installedPlugins.associateBy { it.internalName }
                // Installed first, then by name
                val list = a.value
                    .distinctBy { it.plugin.internalName }
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
                    val isBusy = busy[p.internalName] == true
                    Card {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RemoteImage(p.iconUrl?.replace("%size%", "128"), null, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text("v${p.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    p.language?.let { Tag(it) }
                                    pluginStatusLabel[p.status]?.takeIf { it.isNotEmpty() }?.let { Tag(it) }
                                }
                                p.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                val types = p.tvTypes.orEmpty().joinToString(", ")
                                val authors = p.authors.joinToString(", ")
                                Text(
                                    listOf(types, authors.takeIf { it.isNotBlank() }?.let { "by $it" }).filterNot { it.isNullOrBlank() }.joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                when (state) {
                                    is PluginLoader.LoadState.Failed -> Text("Failed to load: ${state.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    is PluginLoader.LoadState.Loaded -> if (inst != null) Text(
                                        "Loaded: " + state.providers.joinToString().ifEmpty { "extractors only" },
                                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall
                                    )
                                    null -> {}
                                }
                            }
                            when {
                                isBusy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                inst == null -> Button(onClick = { install(rp) }) { Text("Install") }
                                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (p.version > inst.version) Button(onClick = { install(rp) }) { Text("Update") }
                                    OutlinedButton(onClick = { uninstall(p.internalName) }) { Text("Uninstall") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
