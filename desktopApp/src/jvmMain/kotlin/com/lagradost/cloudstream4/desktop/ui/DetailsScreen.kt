package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream4.desktop.Bookmark
import com.lagradost.cloudstream4.desktop.History
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.ic_baseline_arrow_back_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_bookmark_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_bookmark_border_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_keyboard_arrow_down_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_open_in_new_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_play_arrow_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_star_24
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

@Composable
fun DetailsScreen(app: AppState, screen: Screen.Details) {
    val api = remember(screen.apiName) { PluginLoader.providers.value.firstOrNull { it.name == screen.apiName } }
    var result by remember(screen) { mutableStateOf<Loadable<LoadResponse>>(Loadable.Loading) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(screen, reload) {
        result = Loadable.Loading
        result = if (api == null) Loadable.Error("Provider ${screen.apiName} is not loaded")
        else runCatching { withContext(Dispatchers.IO) { api.load(screen.url) } }.fold(
            { if (it == null) Loadable.Error("Nothing found") else Loadable.Ok(it) },
            { Loadable.Error(it.userMessage()) }
        )
    }

    Box(Modifier.fillMaxSize()) {
        when (val r = result) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Error -> MessageBox("Couldn't load this title", r.message, Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CsWhiteButton("Retry") { reload++ }
                    CsBlackButton("Open in browser", Res.drawable.ic_baseline_open_in_new_24) { openInBrowser(screen.url) }
                }
            }
            is Loadable.Ok -> DetailsContent(app, api!!, r.value)
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(Res.drawable.ic_baseline_arrow_back_24, background = Color.Black.copy(alpha = 0.45f)) { app.back() }
        }
    }
}

@Composable
private fun DetailsContent(app: AppState, api: MainAPI, data: LoadResponse) {
    val history by History.state.collectAsState()

    val dubOptions = (data as? AnimeLoadResponse)?.episodes?.filterValues { it.isNotEmpty() }?.keys?.toList().orEmpty()
    var dub by remember(data) { mutableStateOf(dubOptions.firstOrNull { it == DubStatus.Subbed } ?: dubOptions.firstOrNull()) }
    val allItems = remember(data, dub) { data.playItems(dub) }

    val episodes: List<Episode> = when (data) {
        is TvSeriesLoadResponse -> data.episodes
        is AnimeLoadResponse -> dub?.let { data.episodes[it] }.orEmpty()
        else -> emptyList()
    }
    val seasonNames: List<SeasonData> = when (data) {
        is TvSeriesLoadResponse -> data.seasonNames.orEmpty()
        is AnimeLoadResponse -> data.seasonNames.orEmpty()
        else -> emptyList()
    }
    val seasons = episodes.map { it.season }.distinct().sortedBy { it ?: 0 }
    var season by remember(data, dub) { mutableStateOf(seasons.firstOrNull()) }
    val seasonEpisodes = episodes.filter { it.season == season }.sortedBy { it.episode ?: Int.MAX_VALUE }

    fun seasonLabel(s: Int?): String = seasonNames.firstOrNull { it.season == s }?.let { it.name ?: "Season ${it.displaySeason ?: it.season}" }
        ?: if (s == null) "Episodes" else "Season $s"

    // Last watched episode of this show, for the Resume button
    val lastWatched = history.positions.values.filter { it.apiName == api.name && it.showUrl == data.url }.maxByOrNull { it.updatedAt }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 420.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(app, api, data, allItems, lastWatched)
        }

        if (episodes.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (seasons.size > 1) SelectorButton(seasonLabel(season), seasons.map { seasonLabel(it) to it }) { season = it }
                if (dubOptions.size > 1) SelectorButton(dub?.name ?: "", dubOptions.map { it.name to it }) { dub = it }
                Text(
                    "${seasonEpisodes.size} Episodes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        items(seasonEpisodes.size, key = { i -> "ep_${i}_" + seasonEpisodes[i].data }) { i ->
            val ep = seasonEpisodes[i]
            val progress = history.positions["${api.name}|${ep.data}"]
            Box(Modifier.padding(horizontal = 20.dp)) {
                EpisodeCard(ep, progress?.progress) {
                    val idx = allItems.indexOfFirst { it.data == ep.data }.coerceAtLeast(0)
                    app.playFrom(api, data, idx, allItems)
                }
            }
        }

        data.recommendations?.takeIf { it.isNotEmpty() }?.let { recs ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 20.dp, start = 20.dp)) {
                    PosterRow("Recommended", recs) {
                        app.openDetails(PluginLoader.providers.value.firstOrNull { p -> p.name == it.apiName } ?: api, it)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(app: AppState, api: MainAPI, data: LoadResponse, items: List<PlayItem>, lastWatched: com.lagradost.cloudstream4.desktop.WatchEntry?) {
    val history by History.state.collectAsState()
    val bookmarked = history.bookmarks.any { it.apiName == api.name && it.url == data.url }
    var plotExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        // result_poster_background: blurred backdrop fading into the page
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            val backdrop = data.backgroundPosterUrl
            RemoteImage(
                backdrop ?: data.posterUrl, data.posterHeaders, null,
                Modifier.fillMaxSize().then(if (backdrop == null) Modifier.blur(30.dp) else Modifier)
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background.copy(alpha = 0.3f), 1f to MaterialTheme.colorScheme.background)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.8f), Color.Transparent))))
        }

        Row(Modifier.padding(start = 32.dp, end = 32.dp, top = 120.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteImage(
                data.posterUrl, data.posterHeaders, data.name,
                Modifier.width(190.dp).aspectRatio(114f / 180f).clip(RoundedCornerShape(10.dp))
            )
            Column(Modifier.weight(1f).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(data.name, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, color = Color.White)

                // result_meta_*: site, rating, type, year, status, duration
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaText(api.name)
                    data.score?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CsIcon(Res.drawable.ic_baseline_star_24, size = 16.dp, tint = Color(0xFFFFC107))
                            MetaText(" $it/10")
                        }
                    }
                    data.contentRating?.let { Badge(it, Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant) }
                    MetaText(data.type.name)
                    data.year?.let { MetaText(it.toString()) }
                    (data as? TvSeriesLoadResponse)?.showStatus?.let { MetaText(if (it == ShowStatus.Ongoing) "Ongoing" else "Completed") }
                    (data as? AnimeLoadResponse)?.showStatus?.let { MetaText(if (it == ShowStatus.Ongoing) "Ongoing" else "Completed") }
                    data.duration?.let { MetaText("${it} min") }
                }

                data.plot?.htmlToText()?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        maxLines = if (plotExpanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { plotExpanded = !plotExpanded }.pointerHoverIcon(PointerIcon.Hand)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (data is TorrentLoadResponse) {
                        Text("Torrents aren't supported on desktop yet.", color = MaterialTheme.colorScheme.error)
                    } else if (items.isNotEmpty()) {
                        val resumeIdx = lastWatched?.let { lw -> items.indexOfFirst { it.data == lw.episodeData } }?.takeIf { it >= 0 }
                        val label = when {
                            resumeIdx != null && lastWatched.finished && resumeIdx < items.lastIndex -> "Next: ${items[resumeIdx + 1].title}"
                            resumeIdx != null && !lastWatched.finished -> if (items.size > 1) "Resume ${items[resumeIdx].title}" else "Resume ${formatTime(lastWatched.position)}"
                            items.size == 1 -> "Play Movie"
                            else -> "Play ${items.first().title}"
                        }
                        val target = when {
                            resumeIdx != null && lastWatched.finished && resumeIdx < items.lastIndex -> resumeIdx + 1
                            resumeIdx != null -> resumeIdx
                            else -> 0
                        }
                        CsWhiteButton(label, Res.drawable.ic_baseline_play_arrow_24, Modifier.widthIn(min = 200.dp, max = 420.dp)) {
                            app.playFrom(api, data, target, items)
                        }
                    }
                    CsBlackButton(if (bookmarked) "Bookmarked" else "Bookmark", if (bookmarked) Res.drawable.ic_baseline_bookmark_24 else Res.drawable.ic_baseline_bookmark_border_24) {
                        History.toggleBookmark(Bookmark(api.name, data.url, data.name, data.posterUrl, data.type.name, System.currentTimeMillis()))
                    }
                    RoundIconButton(Res.drawable.ic_baseline_open_in_new_24, size = 42.dp, iconSize = 22.dp, tint = MaterialTheme.colorScheme.onBackground) { openInBrowser(data.url) }
                }

                data.actors?.takeIf { it.isNotEmpty() }?.let { actors ->
                    Text("Cast", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(actors.take(30)) { a ->
                            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                RemoteImage(a.actor.image, null, a.actor.name, Modifier.size(60.dp).clip(CircleShape))
                                Text(a.actor.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                                (a.roleString ?: a.voiceActor?.name)?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }

                data.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    Spacer(Modifier.height(2.dp))
                    TagRow { tags.take(16).forEach { Tag(it) } }
                }
                data.trailers.firstOrNull()?.let { t ->
                    Row(Modifier.padding(top = 4.dp)) { CsBlackButton("Trailer", Res.drawable.ic_baseline_play_arrow_24) { openInBrowser(t.extractorUrl) } }
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun <T> SelectorButton(label: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(38.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { open = true }.pointerHoverIcon(PointerIcon.Hand).padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            CsIcon(Res.drawable.ic_baseline_keyboard_arrow_down_24, size = 22.dp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

/** result_episode_large.xml: 16:9 thumbnail with play icon and progress, title, rating, description. */
@Composable
private fun EpisodeCard(ep: Episode, progress: Float?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.width(170.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(ep.posterUrl, null, null, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                CsIcon(Res.drawable.ic_baseline_play_arrow_24, size = 26.dp, tint = Color.White)
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val number = ep.episode?.let { "$it. " } ?: ""
            Text(number + (ep.name ?: "Episode ${ep.episode ?: ""}"), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ep.score?.let { Text("★ $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ep.runTime?.let { Text("${it} min", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ep.description?.htmlToText()?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
