package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(app: AppState) {
    val providers by PluginLoader.providers.collectAsState()
    val homeProviders = providers.filter { it.hasMainPage }
    val state = app.home

    if (providers.isEmpty()) {
        MessageBox(
            "No extensions installed",
            "CloudStream doesn't host any content. Add a repository and install extensions to get started.",
            Modifier.fillMaxSize()
        ) {
            Button(onClick = { app.navigateRoot(Screen.Extensions) }) { Text("Open Extensions") }
        }
        return
    }

    // Pick a provider: the last used one if still available, else the first
    LaunchedEffect(homeProviders.map { it.name }) {
        if (state.providerName == null || homeProviders.none { it.name == state.providerName }) {
            state.providerName = homeProviders.firstOrNull { it.name == SettingsStore.value.lastProvider }?.name
                ?: homeProviders.firstOrNull()?.name
        }
    }
    val api = homeProviders.firstOrNull { it.name == state.providerName }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(api?.name, reloadKey) {
        if (api == null) return@LaunchedEffect
        // Keep cached rows when coming back to the screen
        if (reloadKey == 0 && state.sections.isNotEmpty() && state.sections.none { it.second is Loadable.Error }) return@LaunchedEffect
        loadHome(api, state)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Home", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            ProviderPicker(homeProviders, api) { picked ->
                state.providerName = picked.name
                state.sections.clear()
                SettingsStore.update { it.copy(lastProvider = picked.name) }
            }
            TextButton(onClick = { reloadKey++ }) { Text("Reload") }
        }

        if (homeProviders.isEmpty()) {
            MessageBox("None of your extensions has a home page", "Use Search instead.")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.sections, key = { it.first }) { (name, loadable) ->
                when (loadable) {
                    is Loadable.Loading -> Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text(name.ifBlank { "Loading" }, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(Modifier.width(200.dp).padding(top = 8.dp))
                    }
                    is Loadable.Error -> Text(
                        "${name.ifBlank { "Home" }}: ${loadable.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    is Loadable.Ok -> Column {
                        loadable.value.filter { it.list.isNotEmpty() }.forEach { list ->
                            PosterRow(list.name, list.list, list.isHorizontalImages) { app.openDetails(api, it) }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadHome(api: MainAPI, state: HomeState) = coroutineScope {
    state.sections.clear()
    val pages = api.mainPage.ifEmpty { listOf(com.lagradost.cloudstream3.MainPageData("", "", false)) }
    // Unique keys even when a provider repeats section names
    val keys = pages.mapIndexed { i, p -> "${i}_${p.name}" }
    keys.forEach { state.sections.add(it to Loadable.Loading) }
    pages.forEachIndexed { i, page ->
        launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                }?.items.orEmpty()
            }
            val idx = state.sections.indexOfFirst { it.first == keys[i] }
            if (idx >= 0) {
                state.sections[idx] = keys[i] to result.fold(
                    { Loadable.Ok(it) },
                    { Loadable.Error(it.userMessage()) }
                )
            }
        }
    }
}

@Composable
private fun ProviderPicker(providers: List<MainAPI>, selected: MainAPI?, onPick: (MainAPI) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text((selected?.name ?: "Choose a provider") + "  ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            providers.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.name}  ·  ${p.lang}") },
                    onClick = {
                        open = false
                        onPick(p)
                    }
                )
            }
        }
    }
}
