package com.lagradost.cloudstream4.desktop.ui

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse

fun episodeLabel(showName: String, ep: Episode): String = buildString {
    if (ep.season != null) append("S${ep.season}:")
    if (ep.episode != null) append("E${ep.episode} ")
    append(ep.name ?: if (ep.episode == null) showName else "Episode ${ep.episode}")
}.trim()

/** Ordered episodes for the player (so "Next episode" works), for any LoadResponse. */
fun LoadResponse.playItems(dub: DubStatus? = null): List<PlayItem> = when (this) {
    is MovieLoadResponse -> listOf(PlayItem(name, dataUrl))
    is LiveStreamLoadResponse -> listOf(PlayItem(name, dataUrl))
    is TvSeriesLoadResponse -> episodes.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }))
        .map { PlayItem(episodeLabel(name, it), it.data) }
    is AnimeLoadResponse -> {
        val key = dub ?: episodes.keys.firstOrNull { it == DubStatus.Subbed && !episodes[it].isNullOrEmpty() }
            ?: episodes.keys.firstOrNull { !episodes[it].isNullOrEmpty() }
        key?.let { episodes[it] }.orEmpty().sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }))
            .map { PlayItem(episodeLabel(name, it), it.data) }
    }
    else -> emptyList()
}

fun AppState.playFrom(api: MainAPI, load: LoadResponse, index: Int, items: List<PlayItem> = load.playItems()) {
    if (items.isEmpty()) return
    open(Screen.Player(api.name, load.url, load.name, load.posterUrl, items, index.coerceIn(0, items.lastIndex)))
}

private val brRegex = Regex("(?i)<br\\s*/?>|</p>")
private val htmlHint = Regex("<[a-zA-Z/][^>]*>|&[a-zA-Z#0-9]+;")
private val manyNewlines = Regex("\n{3,}")

/** Providers often return HTML in plots/descriptions (Android shows them with Html.fromHtml). */
fun String.htmlToText(): String {
    if (!htmlHint.containsMatchIn(this)) return this.trim()
    return org.jsoup.Jsoup.parse(replace(brRegex, "\n")).wholeText()
        .lines().joinToString("\n") { it.trim() }
        .replace(manyNewlines, "\n\n")
        .trim()
}
