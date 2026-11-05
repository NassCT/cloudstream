package com.lagradost.cloudstream4.desktop

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking

/** Content "data" strings to pass to loadLinks, for any kind of LoadResponse. */
fun LoadResponse.playableData(): List<Pair<String, String>> = when (this) {
    is MovieLoadResponse -> listOf(name to dataUrl)
    is LiveStreamLoadResponse -> listOf(name to dataUrl)
    is TvSeriesLoadResponse -> episodes.map { (it.name ?: "Episode ${it.episode}") to it.data }
    is AnimeLoadResponse -> episodes.values.flatten().map { (it.name ?: "Episode ${it.episode}") to it.data }
    else -> emptyList()
}

/**
 * Headless end-to-end check, run with `CSP_SELFTEST=1` (optionally CSP_SELFTEST_PLUGIN and
 * CSP_SELFTEST_QUERY). Installs a plugin from the official repo, searches, loads and lists links.
 */
object SelfTest {
    fun run(): Int = runBlocking {
        val pluginName = System.getenv("CSP_SELFTEST_PLUGIN") ?: "InternetArchiveProvider"
        val query = System.getenv("CSP_SELFTEST_QUERY") ?: "nosferatu"
        try {
            val repoUrl = SUGGESTED_REPOSITORIES.first().url
            println("[selftest] repo manifest: ${Repositories.add(repoUrl).name}")
            val plugins = Repositories.fetchPlugins(repoUrl)
            println("[selftest] ${plugins.size} plugins: ${plugins.joinToString { it.plugin.internalName }}")
            val target = plugins.first { it.plugin.internalName == pluginName }
            val state = PluginLoader.install(target)
            println("[selftest] load state: $state")
            check(state is PluginLoader.LoadState.Loaded) { "plugin failed to load" }

            val api = PluginLoader.providers.value.first { it.name in state.providers }
            println("[selftest] provider ${api.name} (${api.mainUrl}) hasMainPage=${api.hasMainPage}")
            val results = api.search(query, 1)?.items.orEmpty()
            println("[selftest] search '$query': ${results.size} results")
            results.take(5).forEach { println("   - ${it.name} | ${it.url}") }
            val first = results.firstOrNull() ?: error("no search results")

            val load = api.load(first.url) ?: error("load returned null")
            println("[selftest] load: ${load.name} type=${load.type} year=${load.year}")
            val data = load.playableData()
            println("[selftest] ${data.size} playable item(s)")
            val links = mutableListOf<ExtractorLink>()
            api.loadLinks(data.first().second, false, { println("   sub: ${it.lang} ${it.url}") }) { links += it }
            println("[selftest] ${links.size} link(s)")
            links.take(5).forEach { println("   - ${it.name} q=${it.quality} ${it.type} audio=${it.audioTracks.size} ${it.url.take(100)}") }
            System.getenv("CSP_SELFTEST_DUMP")?.let { path ->
                java.io.File(path).writeText(links.joinToString("\n") { l ->
                    (listOf("LINK ${l.name} ${l.quality} ${l.url}") + l.audioTracks.map { "AUDIO ${it.url}" }).joinToString("\n")
                })
            }
            check(links.isNotEmpty()) { "no links" }
            println("[selftest] OK")
            0
        } catch (t: Throwable) {
            t.printStackTrace()
            println("[selftest] FAILED: $t")
            1
        }
    }
}
