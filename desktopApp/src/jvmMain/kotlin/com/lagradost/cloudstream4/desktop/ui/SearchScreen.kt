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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.ic_baseline_clear_24
import com.lagradost.cloudstream4.desktop.res.search_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private var searchJob: Job? = null

/** Same groups as the Android search filter chips. */
private val typeFilters: List<Pair<String, Set<TvType>>> = listOf(
    "All" to emptySet(),
    "Movies" to setOf(TvType.Movie, TvType.AnimeMovie, TvType.Torrent),
    "TV Series" to setOf(TvType.TvSeries),
    "Anime" to setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA),
    "Asian" to setOf(TvType.AsianDrama),
    "Cartoons" to setOf(TvType.Cartoon),
    "Documentaries" to setOf(TvType.Documentary),
    "Livestreams" to setOf(TvType.Live),
    "Others" to setOf(TvType.Others),
)

@Composable
fun SearchScreen(app: AppState) {
    val providers by PluginLoader.providers.collectAsState()
    val state = app.search
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun providersFor(types: Set<TvType>) =
        PluginLoader.providers.value.filter { p -> types.isEmpty() || p.supportedTypes.any { it in types } }
    val filteredProviders = providersFor(state.typeFilter)

    fun runSearch() {
        val query = state.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        val filteredProviders = providersFor(state.typeFilter)
        state.lastQuery = query
        state.results.clear()
        filteredProviders.forEach { state.results[it.name] = Loadable.Loading }
        searchJob = scope.launch {
            filteredProviders.forEach { api ->
                launch {
                    val result = runCatching {
                        withTimeout(60_000) { withContext(Dispatchers.IO) { searchProvider(api, query) } }
                    }
                    state.results[api.name] = result.fold({ Loadable.Ok(it) }, { Loadable.Error(it.userMessage()) })
                }
            }
        }
    }

    // Dev helper: CSP_SEARCH="<query>" runs that search when the screen opens (with CSP_SCREEN=Search)
    LaunchedEffect(providers.size) {
        val q = System.getenv("CSP_SEARCH")
        if (!q.isNullOrBlank() && state.lastQuery.isEmpty() && providers.isNotEmpty()) {
            // Restarted on every new provider: runs once extensions stop loading for 5 s
            kotlinx.coroutines.delay(5_000)
            state.query = q
            runSearch()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // search_background: rounded gray box with the search icon
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(25.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CsIcon(Res.drawable.search_icon, size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) Text("Search…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                BasicTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) { runSearch(); true } else false
                    }
                )
            }
            if (state.query.isNotEmpty()) RoundIconButton(Res.drawable.ic_baseline_clear_24, size = 34.dp, iconSize = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) {
                state.query = ""
            }
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            TagRow {
                typeFilters.forEach { (label, types) ->
                    Tag(label, selected = state.typeFilter == types) {
                        state.typeFilter = types
                        if (state.lastQuery.isNotEmpty()) runSearch()
                    }
                }
            }
        }

        if (providers.isEmpty()) {
            MessageBox("No extensions installed", "Install extensions to be able to search.") {
                CsWhiteButton("Open extensions") { app.navigateRoot(Screen.Extensions) }
            }
            return@Column
        }
        if (state.lastQuery.isEmpty()) {
            MessageBox("Search across ${filteredProviders.size} provider(s)", filteredProviders.joinToString(", ") { it.name })
            return@Column
        }

        val ordered = state.results.entries.sortedBy { (_, v) ->
            when (v) {
                is Loadable.Ok -> if (v.value.isNotEmpty()) 0 else 3
                Loadable.Loading -> 1
                is Loadable.Error -> 2
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, bottom = 24.dp)) {
            // No item keys on purpose: with keys the list keeps the first visible row (often an error line)
            // in place while result rows get sorted above it, so results ended up scrolled out of view
            items(ordered) { (name, loadable) ->
                val api = providers.firstOrNull { it.name == name }
                when (loadable) {
                    Loadable.Loading -> Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(Modifier.width(200.dp).padding(top = 10.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    is Loadable.Error -> Text(
                        "$name: ${loadable.message}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    is Loadable.Ok -> {
                        val shown = loadable.value.filterByType(state.typeFilter)
                        if (shown.isEmpty()) Text(
                            "$name: no results", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) else PosterRow(name, shown) { app.openDetails(api, it) }
                    }
                }
            }
        }
    }
}

private fun List<SearchResponse>.filterByType(types: Set<TvType>) =
    if (types.isEmpty()) this else filter { it.type == null || it.type in types }

private suspend fun searchProvider(api: MainAPI, query: String) =
    api.search(query, 1)?.items.orEmpty()
