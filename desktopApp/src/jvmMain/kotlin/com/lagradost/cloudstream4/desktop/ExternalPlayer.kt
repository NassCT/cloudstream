package com.lagradost.cloudstream4.desktop

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays links in an external player. mpv supports arbitrary HTTP headers, VLC only
 * referer and user agent, so mpv is preferred in "Auto" when both are installed.
 */
object ExternalPlayer {
    private fun firstExisting(vararg paths: String?): String? =
        paths.filterNotNull().firstOrNull { File(it).isFile }

    private fun onPath(exe: String): String? =
        System.getenv("PATH")?.split(File.pathSeparator)
            ?.map { File(it, exe) }
            ?.firstOrNull { it.isFile }?.absolutePath

    fun findVlc(): String? {
        val s = SettingsStore.value
        return firstExisting(
            s.vlcPath,
            "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
            "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe",
            "/Applications/VLC.app/Contents/MacOS/VLC",
            "/usr/bin/vlc",
        ) ?: onPath("vlc.exe") ?: onPath("vlc")
    }

    fun findMpv(): String? {
        val s = SettingsStore.value
        val local = System.getenv("LOCALAPPDATA")
        return firstExisting(
            s.mpvPath,
            "C:\\Program Files\\mpv\\mpv.exe",
            local?.let { "$it\\Programs\\mpv\\mpv.exe" },
            System.getenv("USERPROFILE")?.let { "$it\\scoop\\apps\\mpv\\current\\mpv.exe" },
            "/usr/bin/mpv",
            "/opt/homebrew/bin/mpv",
        ) ?: onPath("mpv.exe") ?: onPath("mpv")
    }

    /** Downloads subtitles locally: players can't send the provider's headers for them. */
    private suspend fun downloadSubtitles(subs: List<SubtitleFile>): List<File> {
        val dir = File(AppDirs.cache, "subtitles").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        return subs.mapIndexedNotNull { i, sub ->
            runCatching {
                val ext = Regex("\\.(srt|vtt|ass|ssa)(\\?|$)", RegexOption.IGNORE_CASE)
                    .find(sub.url)?.groupValues?.get(1)?.lowercase() ?: "vtt"
                val safeLang = sub.lang.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(40)
                val file = File(dir, "${i}_$safeLang.$ext")
                file.writeBytes(app.get(sub.url, headers = sub.headers ?: emptyMap()).body.bytes())
                file
            }.getOrNull()
        }
    }

    /** Starts the player. Returns an error message, or null on success. */
    suspend fun play(title: String, link: ExtractorLink, subtitles: List<SubtitleFile>): String? =
        withContext(Dispatchers.IO) {
            val choice = SettingsStore.value.player
            val mpv = if (choice != PlayerChoice.Vlc) findMpv() else null
            val vlc = if (choice != PlayerChoice.Mpv) findVlc() else null
            val subFiles = downloadSubtitles(subtitles)
            val headers = link.getAllHeaders()
            val referer = headers.entries.firstOrNull { it.key.equals("referer", true) }?.value
            val userAgent = headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: USER_AGENT

            val command: List<String> = when {
                mpv != null -> buildList {
                    add(mpv)
                    add("--force-media-title=$title")
                    add("--user-agent=$userAgent")
                    referer?.let { add("--referrer=$it") }
                    val extra = headers.filterKeys { !it.equals("referer", true) && !it.equals("user-agent", true) }
                    if (extra.isNotEmpty()) {
                        // mpv splits this list on commas, so escape them in values
                        add("--http-header-fields=" + extra.entries.joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" })
                    }
                    subFiles.forEach { add("--sub-file=${it.absolutePath}") }
                    add(link.url)
                }
                vlc != null -> buildList {
                    add(vlc)
                    add("--meta-title=$title")
                    add("--http-user-agent=$userAgent")
                    referer?.let { add("--http-referrer=$it") }
                    subFiles.firstOrNull()?.let { add("--sub-file=${it.absolutePath}") }
                    add(link.url)
                }
                else -> return@withContext when (choice) {
                    PlayerChoice.Mpv -> "mpv not found. Install it or set its path in Settings."
                    PlayerChoice.Vlc -> "VLC not found. Install it or set its path in Settings."
                    PlayerChoice.Auto -> "No player found. Install VLC (videolan.org) or mpv, or set the path in Settings."
                }
            }

            runCatching {
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(File(AppDirs.cache, "player.log"))
                    .start()
            }.exceptionOrNull()?.let { "Failed to start the player: ${it.message}" }
        }
}
