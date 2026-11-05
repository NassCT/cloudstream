package com.lagradost.cloudstream4.desktop

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryManifest(
    val name: String,
    val description: String? = null,
    val manifestVersion: Int? = null,
    val pluginLists: List<String> = emptyList(),
    val iconUrl: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OnlinePlugin(
    val url: String,
    val status: Int = 1,
    val version: Int = 0,
    val apiVersion: Int? = null,
    val name: String,
    val internalName: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
)

data class RepoPlugin(val repositoryUrl: String, val plugin: OnlinePlugin)

/** Suggested repositories shown in the Extensions screen. Only the official, legal-content one. */
val SUGGESTED_REPOSITORIES = listOf(
    SavedRepository(
        url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
        name = "Cloudstream providers repository",
        description = "Official repository: Internet Archive, Dailymotion, Invidious, Twitch, YouTube",
    )
)

object Repositories {
    private val schemeRegex = Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)")

    /** Same rules as the Android app: full URL, cloudstreamrepo:// links or short codes. */
    suspend fun resolveUrl(input: String): String? {
        val fixed = input.trim()
        return when {
            fixed.contains(Regex("^https?://")) -> fixed
            fixed.contains(schemeRegex) -> fixed.replace(schemeRegex, "").let {
                if (it.contains(Regex("^https?://"))) it else "https://$it"
            }
            fixed.matches(Regex("^[a-zA-Z0-9!_-]+$")) -> runCatching {
                val (host, code) = if (fixed.startsWith("!")) "https://py.md" to fixed.removePrefix("!")
                else "https://cutt.ly" to fixed
                val response = app.get("$host/$code", allowRedirects = false)
                val location = response.headers["Location"] ?: return null
                if (location.startsWith("$host/404") || location.removeSuffix("/") == host) null else location
            }.getOrNull()
            else -> null
        }
    }

    /** github.com/<user>/<repo>/blob/<branch>/file -> raw.githubusercontent.com/... */
    fun convertRawGitUrl(url: String): String {
        val match = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$rest"
    }

    suspend fun fetchManifest(url: String): RepositoryManifest =
        desktopMapper.readValue(app.get(convertRawGitUrl(url)).text)

    suspend fun fetchPlugins(repositoryUrl: String): List<RepoPlugin> = coroutineScope {
        val manifest = fetchManifest(repositoryUrl)
        manifest.pluginLists.map { listUrl ->
            async {
                runCatching {
                    desktopMapper.readValue<List<OnlinePlugin>>(app.get(convertRawGitUrl(listUrl)).text)
                }.onFailure { it.printStackTrace() }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().map { RepoPlugin(repositoryUrl, it) }
    }

    /** Resolves, validates and saves a repository. Returns its manifest. */
    suspend fun add(input: String): RepositoryManifest {
        val url = resolveUrl(input) ?: error("Invalid repository URL or code: $input")
        val manifest = fetchManifest(url)
        SettingsStore.update { s ->
            if (s.repositories.any { it.url == url }) s
            else s.copy(
                repositories = s.repositories + SavedRepository(url, manifest.name, manifest.iconUrl, manifest.description)
            )
        }
        return manifest
    }

    fun remove(url: String) {
        SettingsStore.update { s -> s.copy(repositories = s.repositories.filterNot { it.url == url }) }
    }
}
