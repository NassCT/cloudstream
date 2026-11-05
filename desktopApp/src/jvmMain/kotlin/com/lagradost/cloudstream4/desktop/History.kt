package com.lagradost.cloudstream4.desktop

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One episode/movie being watched, used for "Continue watching" and resume. */
data class WatchEntry(
    val apiName: String,
    val showUrl: String,
    val showName: String,
    val posterUrl: String?,
    val episodeTitle: String,
    val episodeData: String,
    val episodeIndex: Int,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = duration > 0 && position > duration * 0.92
}

/** Saved titles, like the Android library bookmarks. */
data class Bookmark(
    val apiName: String,
    val url: String,
    val name: String,
    val posterUrl: String?,
    val type: String?,
    val addedAt: Long,
)

data class HistoryData(
    /** Key: "$apiName|$episodeData". */
    val positions: Map<String, WatchEntry> = emptyMap(),
    val bookmarks: List<Bookmark> = emptyList(),
)

object History {
    private val file = File(AppDirs.root, "history.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<HistoryData> = _state.asStateFlow()

    private fun load(): HistoryData = runCatching {
        if (file.exists()) desktopMapper.readValue<HistoryData>(file) else HistoryData()
    }.getOrElse { HistoryData() }

    @Synchronized
    private fun update(transform: (HistoryData) -> HistoryData) {
        _state.value = transform(_state.value)
        runCatching {
            val tmp = File(file.parentFile, "history.json.tmp")
            desktopMapper.writeValue(tmp, _state.value)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun key(apiName: String, data: String) = "$apiName|$data"

    fun positionOf(apiName: String, data: String): WatchEntry? = _state.value.positions[key(apiName, data)]

    fun savePosition(entry: WatchEntry) {
        // Ignore the first seconds, they're not worth resuming
        if (entry.position < 10_000 && entry.duration > 0) return
        update { it.copy(positions = it.positions + (key(entry.apiName, entry.episodeData) to entry)) }
    }

    /** Latest unfinished entry per show, newest first. */
    fun continueWatching(): List<WatchEntry> =
        _state.value.positions.values
            .groupBy { it.apiName + "|" + it.showUrl }
            .mapNotNull { (_, entries) -> entries.maxByOrNull { it.updatedAt } }
            .filterNot { it.finished }
            .sortedByDescending { it.updatedAt }

    fun removeShow(apiName: String, showUrl: String) =
        update { d -> d.copy(positions = d.positions.filterValues { !(it.apiName == apiName && it.showUrl == showUrl) }) }

    fun isBookmarked(apiName: String, url: String) = _state.value.bookmarks.any { it.apiName == apiName && it.url == url }

    fun toggleBookmark(b: Bookmark) = update { d ->
        if (d.bookmarks.any { it.apiName == b.apiName && it.url == b.url })
            d.copy(bookmarks = d.bookmarks.filterNot { it.apiName == b.apiName && it.url == b.url })
        else d.copy(bookmarks = listOf(b) + d.bookmarks)
    }
}
