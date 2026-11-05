package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.desktop.Bookmark
import com.lagradost.cloudstream4.desktop.History
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.SettingsStore
import com.lagradost.cloudstream4.desktop.WatchEntry
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.ic_baseline_bookmark_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_bookmark_border_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_keyboard_arrow_down_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_play_arrow_24
import com.lagradost.cloudstream4.desktop.res.ic_outline_info_24
import com.lagradost.cloudstream4.desktop.res.search_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Details of hero items, loaded lazily for the background image, logo and plot. */
private val heroDetails = mutableStateMapOf<String, LoadResponse?>()

@Composable
fun HomeScreen(app: AppState) {
    val providers by PluginLoader.providers.collectAsState()
    val homeProviders = providers.filter { it.hasMainPage }
    val state = app.home
    var pickerOpen by remember { mutableStateOf(false) }

    if (providers.isEmpty()) {
        MessageBox(
            "No extensions installed",
            "CloudStream doesn't host any content.\nAdd a repository and install extensions to get started.",
            Modifier.fillMaxSize()
        ) { CsWhiteButton("Open extensions") { app.navigateRoot(Screen.Extensions) } }
        return
    }

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
        if (reloadKey == 0 && state.sections.isNotEmpty() && state.sections.none { it.second is Loadable.Error }) return@LaunchedEffect
        loadHome(api, state)
    }

    val history by History.state.collectAsState()
    val continueWatching = remember(history) { History.continueWatching() }

    val lists = state.sections.mapNotNull { (it.second as? Loadable.Ok)?.value }.flatten().filter { it.list.isNotEmpty() }
    val heroItems = lists.firstOrNull()?.list?.take(8).orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.66f).coerceIn(340.dp, 640.dp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    if (heroItems.isNotEmpty() && api != null) Hero(app, api, heroItems)
                    else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (state.sections.any { it.second is Loadable.Loading }) CenteredLoading()
                    }
                    // Top bar over the hero: search box + provider picker (fragment_home_head)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            Modifier.weight(1f).widthIn(max = 520.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { app.navigateRoot(Screen.Search) }.pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CsIcon(Res.drawable.search_icon, size = 22.dp, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text("Search", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(0.01f))
                        Row(
                            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { pickerOpen = true }.pointerHoverIcon(PointerIcon.Hand)
                                .padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(api?.name ?: "Select provider", color = Color.White, fontWeight = FontWeight.Bold)
                            CsIcon(Res.drawable.ic_baseline_keyboard_arrow_down_24, size = 22.dp, tint = Color.White)
                        }
                    }
                }
            }

            if (continueWatching.isNotEmpty()) item(key = "continue") {
                ContinueWatchingRow(app, continueWatching)
            }

            if (homeProviders.isEmpty()) item {
                MessageBox("None of your extensions has a home page", "Use Search instead.")
            }

            state.sections.forEach { (name, loadable) ->
                when (loadable) {
                    is Loadable.Loading -> item(key = name) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                            Text(name.substringAfter('_').ifBlank { "Loading" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(Modifier.width(200.dp).padding(top = 10.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is Loadable.Error -> item(key = name) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${name.substringAfter('_').ifBlank { "Home" }}: ${loadable.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            TextButton(onClick = { reloadKey++ }) { Text("Retry") }
                        }
                    }
                    is Loadable.Ok -> loadable.value.filter { it.list.isNotEmpty() }.forEachIndexed { i, list ->
                        item(key = "$name#$i") {
                            PosterRow(list.name, list.list, list.isHorizontalImages) { app.openDetails(api, it) }
                        }
                    }
                }
            }
        }
    }

    if (pickerOpen) ProviderPickerDialog(homeProviders, api, onDismiss = { pickerOpen = false }) { picked ->
        pickerOpen = false
        if (picked.name != state.providerName) {
            state.providerName = picked.name
            state.sections.clear()
            SettingsStore.update { it.copy(lastProvider = picked.name) }
        }
    }
}

@Composable
private fun Hero(app: AppState, api: MainAPI, items: List<SearchResponse>) {
    var index by remember(items) { mutableIntStateOf(0) }
    val item = items[index.coerceIn(0, items.lastIndex)]
    val key = api.name + "|" + item.url
    val details = heroDetails[key]

    // Auto-advance like the Android view pager, and fetch details for the current item
    LaunchedEffect(items) {
        while (true) {
            delay(9000)
            index = (index + 1) % items.size
        }
    }
    LaunchedEffect(key) {
        if (heroDetails.containsKey(key)) return@LaunchedEffect
        heroDetails[key] = null
        heroDetails[key] = runCatching { withContext(Dispatchers.IO) { api.load(item.url) } }.getOrNull()
    }

    AnimatedContent(item, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.fillMaxSize()) { shown ->
        val d = heroDetails[api.name + "|" + shown.url]
        Box(Modifier.fillMaxSize()) {
            RemoteImage(
                d?.backgroundPosterUrl ?: d?.posterUrl ?: shown.posterUrl,
                d?.posterHeaders ?: shown.posterHeaders, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // background_shadow.xml at top and bottom, plus a left fade for readability
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to MaterialTheme.colorScheme.background)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.6f).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.75f), Color.Transparent))))
        }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val logo = details?.logoUrl
            if (logo != null) {
                RemoteImage(logo, details.posterHeaders, item.name, Modifier.height(90.dp).widthIn(max = 360.dp), contentScale = ContentScale.Fit, placeholder = Color.Transparent)
            } else {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 38.sp)
            }
            val meta = listOfNotNull(details?.year?.toString(), details?.score?.let { "★ $it" }, details?.tags?.take(4)?.joinToString(" • ")).joinToString("   ")
            if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            details?.plot?.htmlToText()?.let { Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CsWhiteButton("Play", Res.drawable.ic_baseline_play_arrow_24, Modifier.width(140.dp)) {
                    val d = details
                    if (d != null && d.playItems().size == 1) app.playFrom(api, d, 0) else app.openDetails(api, item)
                }
                val history by History.state.collectAsState()
                val bookmarked = history.bookmarks.any { it.apiName == api.name && it.url == item.url }
                IconLabelButton(if (bookmarked) Res.drawable.ic_baseline_bookmark_24 else Res.drawable.ic_baseline_bookmark_border_24, if (bookmarked) "Saved" else "Bookmark") {
                    History.toggleBookmark(Bookmark(api.name, item.url, item.name, item.posterUrl, item.type?.name, System.currentTimeMillis()))
                }
                IconLabelButton(Res.drawable.ic_outline_info_24, "Info") { app.openDetails(api, item) }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Page indicators
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == index) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (i == index) Color.White else Color.White.copy(alpha = 0.4f))
                        .clickable { index = i }.pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(app: AppState, entries: List<WatchEntry>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Continue watching", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries.size, key = { i -> "cw_${i}_" + entries[i].episodeData }) { i ->
                val e = entries[i]
                Column(Modifier.width(128.dp)) {
                    Box(
                        Modifier.width(128.dp).height(128.dp * 180f / 114f).clip(RoundedCornerShape(10.dp))
                            .clickable { app.open(Screen.Details(e.apiName, e.showUrl, e.showName)) }
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        RemoteImage(e.posterUrl, null, e.showName, Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                            CsIcon(Res.drawable.ic_baseline_play_arrow_24, size = 30.dp, tint = Color.White)
                        }
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f)))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(e.progress).height(4.dp).background(MaterialTheme.colorScheme.primary))
                    }
                    Text(e.showName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (e.episodeTitle != e.showName) Text(e.episodeTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private suspend fun loadHome(api: MainAPI, state: HomeState) = coroutineScope {
    state.sections.clear()
    val pages = api.mainPage.ifEmpty { listOf(MainPageData("", "", false)) }
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
                state.sections[idx] = keys[i] to result.fold({ Loadable.Ok(it) }, { Loadable.Error(it.userMessage()) })
            }
        }
    }
}

/** Like home_select_mainpage.xml: pick the provider whose home page is shown. */
@Composable
private fun ProviderPickerDialog(providers: List<MainAPI>, selected: MainAPI?, onDismiss: () -> Unit, onPick: (MainAPI) -> Unit) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Select provider", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.width(420.dp)) {
                OutlinedTextField(filter, { filter = it }, singleLine = true, placeholder = { Text("Filter") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(providers.filter { filter.isBlank() || it.name.contains(filter, true) }, key = { it.name }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(p) }
                                .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                p.name, modifier = Modifier.weight(1f), fontSize = 16.sp,
                                color = if (p == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (p == selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(p.lang.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
