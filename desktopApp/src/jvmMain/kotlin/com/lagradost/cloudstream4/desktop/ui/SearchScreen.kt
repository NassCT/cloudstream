package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream4.desktop.PluginLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private var searchJob: Job? = null

@Composable
fun SearchScreen(app: AppState) {
    val providers by PluginLoader.providers.collectAsState()
    val state = app.search
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun runSearch() {
        val query = state.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        state.lastQuery = query
        state.results.clear()
        providers.forEach { state.results[it.name] = Loadable.Loading }
        searchJob = scope.launch {
            providers.forEach { api ->
                launch {
                    val result = runCatching {
                        withTimeout(60_000) {
                            withContext(Dispatchers.IO) { searchProvider(api, query) }
                        }
                    }
                    state.results[api.name] = result.fold({ Loadable.Ok(it) }, { Loadable.Error(it.userMessage()) })
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                placeholder = { Text("Search movies, series, anime…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            runSearch(); true
                        } else false
                    }
            )
            Button(onClick = { runSearch() }, modifier = Modifier.padding(top = 8.dp)) { Text("Search") }
        }

        if (providers.isEmpty()) {
            MessageBox("No extensions installed", "Install extensions to be able to search.") {
                Button(onClick = { app.navigateRoot(Screen.Extensions) }) { Text("Open Extensions") }
            }
            return@Column
        }

        if (state.lastQuery.isEmpty()) {
            MessageBox("Search across ${providers.size} provider(s)", providers.joinToString(", ") { it.name })
            return@Column
        }

        // Providers with results first, then loading ones, then empty / failed
        val ordered = state.results.entries.sortedBy { (_, v) ->
            when (v) {
                is Loadable.Ok -> if (v.value.isNotEmpty()) 0 else 3
                Loadable.Loading -> 1
                is Loadable.Error -> 2
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ordered, key = { it.key }) { (name, loadable) ->
                val api = providers.firstOrNull { it.name == name }
                when (loadable) {
                    Loadable.Loading -> Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(Modifier.width(200.dp).padding(top = 8.dp))
                    }
                    is Loadable.Error -> Text(
                        "$name: ${loadable.message}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    is Loadable.Ok -> if (loadable.value.isEmpty()) Text(
                        "$name: no results",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    ) else PosterRow("$name (${loadable.value.size})", loadable.value) { app.openDetails(api, it) }
                }
            }
        }
    }
}

private suspend fun searchProvider(api: MainAPI, query: String) =
    api.search(query, 1)?.items.orEmpty()
