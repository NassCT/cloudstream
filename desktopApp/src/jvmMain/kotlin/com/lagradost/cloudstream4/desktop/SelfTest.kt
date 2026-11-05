package com.lagradost.cloudstream4.desktop

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.async
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
    fun run(): Int = when (System.getenv("CSP_SELFTEST")) {
        "installed" -> runInstalled()
        "links" -> runLinks()
        else -> runOne()
    }

    /**
     * `CSP_SELFTEST=links`: for each installed provider (or CSP_SELFTEST_PROVIDERS, comma separated),
     * search CSP_SELFTEST_QUERY, open the first result and collect its video links, like pressing Play.
     */
    private fun runLinks(): Int = runBlocking {
        val query = System.getenv("CSP_SELFTEST_QUERY") ?: "dune"
        val only = System.getenv("CSP_SELFTEST_PROVIDERS")?.split(",")?.map { it.trim().lowercase() }?.toSet()
        PluginLoader.loadAllInstalled()
        val apis = PluginLoader.providers.value.filter { only == null || it.name.lowercase() in only }
        val rows = kotlinx.coroutines.coroutineScope {
            val scope = this
            apis.map { api ->
                scope.async(kotlinx.coroutines.Dispatchers.IO) {
                    val log = StringBuilder()
                    val outcome = runCatching {
                        kotlinx.coroutines.withTimeout(90_000) {
                            val first = api.search(query, 1)?.items.orEmpty().firstOrNull() ?: return@withTimeout "no search results"
                            val load = api.load(first.url) ?: return@withTimeout "load returned null (${first.name})"
                            val data = load.playableData().firstOrNull() ?: return@withTimeout "nothing playable in ${load.name} (${load.type})"
                            val links = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
                            var subs = 0
                            val ok = runCatching { api.loadLinks(data.second, false, { subs++ }) { links += it } }
                            ok.exceptionOrNull()?.let { log.append(" loadLinks threw ${it::class.simpleName}: ${it.message?.take(120)}") }
                            links.take(4).forEach { log.append("\n      - ${it.source} q=${it.quality} ${it.type} ${it.url.take(90)}") }
                            "${load.name} / ${data.first}: ${links.size} link(s), $subs sub(s)"
                        }
                    }.getOrElse {
                        val root = generateSequence(it) { e -> e.cause }.last()
                        "ERROR ${root::class.simpleName}: ${root.message?.take(150)}"
                    }
                    "[links] ${api.name}: $outcome$log".also { println(it) }
                }
            }
        }
        // Some sites never answer and blocking calls ignore cancellation: give up after 4 minutes
        val done = kotlinx.coroutines.withTimeoutOrNull(240_000) { rows.map { it.await() } }
        if (done == null) println("[links] stopped waiting: " + apis.zip(rows).filter { !it.second.isCompleted }.joinToString { it.first.name } + " never answered")
        kotlin.system.exitProcess(0)
    }

    /**
     * `CSP_SELFTEST=installed`: loads every installed extension and searches each provider
     * (CSP_SELFTEST_QUERY, default "batman"), to spot extensions that don't work on desktop.
     */
    private fun runInstalled(): Int = runBlocking {
        val query = System.getenv("CSP_SELFTEST_QUERY") ?: "batman"
        PluginLoader.loadAllInstalled()
        PluginLoader.states.value.toSortedMap().forEach { (name, state) -> println("[load] $name: $state") }
        val failures = PluginLoader.states.value.count { it.value is PluginLoader.LoadState.Failed }
        PluginLoader.providers.value.forEach { api ->
            val result = runCatching {
                kotlinx.coroutines.withTimeout(30_000) { api.search(query, 1)?.items.orEmpty() }
            }
            result.onSuccess { println("[search] ${api.name}: ${it.size} results ${it.firstOrNull()?.name ?: ""}") }
                .onFailure {
                    val root = generateSequence(it) { e -> e.cause }.last()
                    println("[search] ${api.name}: ERROR ${root::class.simpleName}: ${root.message}")
                }
        }
        println("[selftest] $failures extension(s) failed to load")
        if (failures == 0) 0 else 1
    }

    private fun runOne(): Int = runBlocking {
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
