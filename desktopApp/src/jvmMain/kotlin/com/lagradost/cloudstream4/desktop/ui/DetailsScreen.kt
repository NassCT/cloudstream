package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream4.desktop.ExternalPlayer
import com.lagradost.cloudstream4.desktop.PluginLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

private data class PlayRequest(val title: String, val data: String)

@Composable
fun DetailsScreen(app: AppState, screen: Screen.Details) {
    val api = remember(screen.apiName) { PluginLoader.providers.value.firstOrNull { it.name == screen.apiName } }
    var result by remember(screen) { mutableStateOf<Loadable<LoadResponse>>(Loadable.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var play by remember { mutableStateOf<PlayRequest?>(null) }

    LaunchedEffect(screen, reload) {
        result = Loadable.Loading
        result = if (api == null) Loadable.Error("Provider ${screen.apiName} is not loaded")
        else runCatching { withContext(Dispatchers.IO) { api.load(screen.url) } }.fold(
            { if (it == null) Loadable.Error("Nothing found") else Loadable.Ok(it) },
            { Loadable.Error(it.userMessage()) }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { app.back() }) { Text("‹  Back") }
            Text(screen.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (val r = result) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Error -> MessageBox("Couldn't load this title", r.message) {
                Button(onClick = { reload++ }) { Text("Retry") }
            }
            is Loadable.Ok -> DetailsContent(app, api!!, r.value) { play = it }
        }
    }

    play?.let { req ->
        LinksDialog(app, api!!, req) { play = null }
    }
}

@Composable
private fun DetailsContent(app: AppState, api: MainAPI, data: LoadResponse, onPlay: (PlayRequest) -> Unit) {
    // Anime: pick dub/sub first
    val dubOptions = (data as? AnimeLoadResponse)?.episodes?.filterValues { it.isNotEmpty() }?.keys?.toList().orEmpty()
    var dub by remember(data) { mutableStateOf(dubOptions.firstOrNull { it == DubStatus.Subbed } ?: dubOptions.firstOrNull()) }

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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Header(data, api, onPlay) }

        if (dubOptions.size > 1) item {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                TagRow { dubOptions.forEach { d -> Tag(d.name, selected = d == dub) { dub = d } } }
            }
        }
        if (seasons.size > 1) item {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                TagRow {
                    seasons.forEach { s ->
                        val label = seasonNames.firstOrNull { it.season == s }?.let { it.name ?: "Season ${it.displaySeason ?: it.season}" }
                            ?: if (s == null) "Other" else "Season $s"
                        Tag(label, selected = s == season) { season = s }
                    }
                }
            }
        }
        if (episodes.isNotEmpty()) {
            item {
                Text(
                    "${seasonEpisodes.size} episode(s)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            items(seasonEpisodes, key = { it.data + it.episode + it.season }) { ep ->
                EpisodeRow(ep) {
                    val label = buildString {
                        append(data.name)
                        if (ep.season != null) append(" S${ep.season}")
                        if (ep.episode != null) append("E${ep.episode}")
                        ep.name?.let { append(" - $it") }
                    }
                    onPlay(PlayRequest(label, ep.data))
                }
            }
        }

        data.recommendations?.takeIf { it.isNotEmpty() }?.let { recs ->
            item {
                Spacer(Modifier.height(16.dp))
                PosterRow("Recommendations", recs) { app.openDetails(PluginLoader.providers.value.firstOrNull { p -> p.name == it.apiName } ?: api, it) }
            }
        }
    }
}

@Composable
private fun Header(data: LoadResponse, api: MainAPI, onPlay: (PlayRequest) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // Blurred-ish backdrop: the background poster, faded into the page background
        val backdrop = data.backgroundPosterUrl ?: data.posterUrl
        if (backdrop != null) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                RemoteImage(backdrop, data.posterHeaders, null, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(heroGradient))
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)))
            }
        }
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RemoteImage(
                data.posterUrl, data.posterHeaders, data.name,
                Modifier.width(200.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(data.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val meta = listOfNotNull(
                    data.year?.toString(),
                    data.type.name,
                    data.duration?.let { "$it min" },
                    data.score?.let { "★ $it/10" },
                    data.contentRating,
                    api.name,
                ).joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.tags?.takeIf { it.isNotEmpty() }?.let { tags -> TagRow { tags.take(12).forEach { Tag(it) } } }
                data.plot?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                data.actors?.takeIf { it.isNotEmpty() }?.let { actors ->
                    Text(
                        "Cast: " + actors.take(8).joinToString(", ") { it.actor.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val single = when (data) {
                        is MovieLoadResponse -> data.dataUrl
                        is LiveStreamLoadResponse -> data.dataUrl
                        is TorrentLoadResponse -> null
                        else -> null
                    }
                    if (single != null) {
                        Button(onClick = { onPlay(PlayRequest(data.name, single)) }) { Text("▶  Play") }
                    }
                    if (data is TorrentLoadResponse) {
                        Text("Torrents are not supported yet on desktop.", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = { openInBrowser(data.url) }) { Text("Open website") }
                    data.trailers.firstOrNull()?.let { t ->
                        OutlinedButton(onClick = { openInBrowser(t.extractorUrl) }) { Text("Trailer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(ep.posterUrl, null, null, Modifier.fillMaxSize())
            Text(
                "▶", color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            val number = ep.episode?.let { "$it. " } ?: ""
            Text(number + (ep.name ?: "Episode ${ep.episode ?: ""}"), style = MaterialTheme.typography.titleSmall)
            ep.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun qualityLabel(q: Int): String = when {
    q <= 0 -> "Auto"
    else -> "${q}p"
}

@Composable
private fun LinksDialog(app: AppState, api: MainAPI, req: PlayRequest, onDismiss: () -> Unit) {
    val links = remember(req) { mutableStateListOf<ExtractorLink>() }
    val subs = remember(req) { mutableStateListOf<SubtitleFile>() }
    var loading by remember(req) { mutableStateOf(true) }
    var error by remember(req) { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(req) {
        runCatching {
            withContext(Dispatchers.IO) {
                api.loadLinks(req.data, false, { s ->
                    if (subs.none { it.url == s.url }) subs.add(s)
                }) { l ->
                    if (links.none { it.url == l.url }) links.add(l)
                }
            }
        }.onFailure { error = it.userMessage() }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(req.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.width(560.dp).heightIn(max = 480.dp)) {
                if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Looking for links… (${links.size} found)")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loading && links.isEmpty() && error == null) Text("No links found.")
                if (subs.isNotEmpty()) Text(
                    "${subs.size} subtitle(s): " + subs.joinToString(", ") { it.lang },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                LazyColumn {
                    items(links.sortedByDescending { it.quality }, key = { it.url }) { link ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !starting) {
                                    starting = true
                                    scope.launch {
                                        val err = ExternalPlayer.play(req.title, link, subs.toList())
                                        starting = false
                                        if (err != null) app.toast(err) else {
                                            app.toast("Playing in external player")
                                            onDismiss()
                                        }
                                    }
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Tag(qualityLabel(link.quality))
                            Column(Modifier.weight(1f)) {
                                Text(link.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${link.source} · ${link.type.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link.url), null)
                                app.toast("Link copied")
                            }) { Text("Copy") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
